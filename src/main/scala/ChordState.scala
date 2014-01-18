package momijikawa.p2pscalaproto

import scalaz._
import Scalaz._
import akka.actor._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.immutable.HashMap
import akka.agent.Agent
import scala.concurrent.stm._
import scala.util.control.Exception._

/**
 * ノードの状態の要素。
 * このクラスがノードの持ちうる全ての情報です。
 * @param selfID このノードのID
 * @param succList Successorのリスト
 * @param fingerList FingerTable
 * @param pred Predecessor
 * @param dataholder データの格納場所
 * @param stabilizer 安定化処理を行なうためのタイマ
 */
case class ChordState(
                       selfID: Option[idAddress],
                       succList: NodeList,
                       fingerList: NodeList,
                       pred: Option[idAddress],
                       dataholder: HashMap[Seq[Byte], KVSData],
                       stabilizer: ActorRef) {
  def dropNode(a: ActorRef): ChordState = {
    this.copy(selfID, succList.remove(a), fingerList.replace(a, selfID.get), pred.flatMap((i) => if (i.a == a) None else i.some), dataholder, stabilizer)
  }
}

/**
 * [[momijikawa.p2pscalaproto.ChordState]]を変更するための静的なメソッド群。
 */
object ChordState {
  type dataMap = HashMap[Seq[Byte], Seq[Byte]]
  type idListT = List[idAddress]

  /** [[momijikawa.p2pscalaproto.ChordState.succList]]と[[momijikawa.p2pscalaproto.ChordState.fingerList]]のどちらを処理するかの型 */
  sealed trait SL_FL

  case object SL extends SL_FL

  case object FL extends SL_FL

  /**
   * """このノードに"""データを保管します。
   * DHTではなくこのノードにデータを保存します。
   * @param key キー
   * @param value データ
   * @param state [[momijikawa.p2pscalaproto.ChordState]]の[[akka.agent.Agent]]
   * @return 成功した場合は[[scala.Option]]としてキーが返ります。
   */
  def putDataToNode(key: Seq[Byte], value: KVSData, state: Agent[ChordState]): Option[Seq[Byte]] =
    atomic {
      implicit txn =>
        allCatch opt {
          state send {
            (st) => st.copy(dataholder = st.dataholder + ((key, value)))
          }
          key
        }
    }

  /**
   * ブートストラップを経由してDHTネットワークに参加します。
   * このメソッドは[[momijikawa.p2pscalaproto.ChordState]]の[[akka.agent.Agent]]を変更します。
   * @param target ブートストラップとして利用するノード。
   * @param state 参加するノードの[[momijikawa.p2pscalaproto.ChordState]]の[[akka.agent.Agent]]
   * @return 新たなSuccessor
   */
  def joinNetwork(target: idAddress, state: Agent[ChordState]): Option[idAddress] = atomic {
    implicit txn =>
      for {
        selfID <- state().selfID
        mightBeNewSuccessor <- (allCatch opt Await.result(target.getClient(selfID).findNode(selfID), 10 second).idaddress).flatten[idAddress]
        _ <- (state send {
          _.copy(succList = NodeList(List[idAddress](mightBeNewSuccessor).toNel.get), pred = None)
        }).some
        _ <- (state().stabilizer ! StartStabilize).some
      } yield mightBeNewSuccessor
  }

  val joinNetworkS: idAddress => State[ChordState, Option[idAddress]] = (target: idAddress) =>
    State[ChordState, Option[idAddress]] {
      cs => {
        for {
          sid <- cs.selfID
          newSucc <- (allCatch opt findSelf(target, sid)).flatten[idAddress]
          newState <- regenerateState(cs, newSucc).some
          _ <- startStabilize(newState).some
        } yield (newState, newSucc.some)
      } |(cs, None)
    }

  private def findSelf(target: idAddress, sid: idAddress): Option[idAddress] = Await.result(target.getClient(sid).findNode(sid), 10 second).idaddress

  private def regenerateState(cs: ChordState, newSucc: idAddress): ChordState = cs.copy(succList = NodeList(List[idAddress](newSucc).toNel.get), pred = None)

  private def startStabilize(cs: ChordState): Unit = cs.stabilizer ! StartStabilize

  /**
   * あるノードIDを管轄するノードを検索します。
   * 自分とノードIDが同じであるか、もしくは自分が担当のノードであるときは自分を示す[[momijikawa.p2pscalaproto.IdAddressMessage]]を発信者に送り返します。
   * そうでないときは、最も近いと推定されるノードに問い合わせを転送します。
   * @param csa 自分のノードの[[momijikawa.p2pscalaproto.ChordState]]の[[akka.agent.Agent]]
   * @param id_query 検索したいノードID
   * @param context 発信者が分かる文脈情報
   */
  def findNode(csa: Agent[ChordState], id_query: TnodeID)(implicit context: ActorContext) = atomic {
    implicit txn =>
      val isMeAndQueryEqual = csa().selfID.get.getNodeID == id_query
      val id_nearest: idAddress = NodeList(csa().succList.nodes.list ++ csa().fingerList.nodes.list).nearestSuccessor(id_self = csa().selfID.get)

      isMeAndQueryEqual match {
        case true =>
          context.sender ! IdAddressMessage(idaddress = csa().selfID)

        case false =>
          val isQueryBelongsNearest = id_query belongs_between csa().selfID.get and id_nearest

          isQueryBelongsNearest match {
            case true =>
              val cliNrst = id_nearest.getClient(selfid = csa().selfID.get)
              val address: Option[idAddress] = (id_nearest.getNodeID == csa().selfID.get.getNodeID) match {
                case true => csa().selfID
                case false => cliNrst.whoAreYou
              }
              context.sender ! IdAddressMessage(idaddress = address)

            case false =>
              val id_nearestForwarder: idAddress = NodeList(csa().succList.nodes.list ++ csa().fingerList.nodes.list).nearestSuccessor(id_query)
              (id_nearestForwarder.getNodeID == csa().selfID.get) match {
                case true => context.sender ! IdAddressMessage(idaddress = csa().selfID)
                case false => id_nearestForwarder.actorref.forward(FindNode(id_query.getBase64))
              }
          }
      }
  }

  /**
   * 現在分かる範囲における、自分に最も近いSuccessorを返します。
   */
  val yourSuccessor: (ChordState) => Option[idAddress] = (cs: ChordState) => cs.selfID map {
    selfID => cs.succList.nearestSuccessor(selfID)
  } //cs.succList.last.some

  /**
   * 自分のPredecessorを返します。
   * @param cs 自分のノードの状態。
   * @param context
   * @return Predecessor
   */
  def yourPredecessor(cs: ChordState)(implicit context: ActorContext): Option[idAddress] = {
    context.system.log.debug("YourPredecessorCore")
    // ここでもsuccの自動変更が必要？
    cs.stabilizer ! StartStabilize
    cs.pred
  }

  /**
   * 現在のPredecessorと比較し、最適な状態になるべく調整します。
   * 渡された[[momijikawa.p2pscalaproto.idAddress]]と現在のPredecessorを比較し、原則として、より近い側をPredecessorとして決め、ノードの状態を更新します。
   * また、渡されたものと自分が同じノードであるときは変更しません。
   * また、現在のPredecessorが利用できないときには渡された[[momijikawa.p2pscalaproto.idAddress]]を受け入れます。
   * @param sender 比較対象のPredecessor。
   * @param state 自分のノードの状態。
   */
  def checkPredecessor(sender: idAddress, state: Agent[ChordState])(implicit context: ActorContext) = {
    // selfがsaidではなく、指定した条件に合致した場合には交換の必要ありと判断する
    // 送信元がselfの場合は無視し、そうでないときは検査開始
    require(state().selfID.isDefined)

    // ----- //
    val check: (ChordState) => Boolean =
      (st) => allCatch.opt {
        context.system.log.debug("ChordState: checking my predecessor")

        // when pred is self?
        val isSenderSelf = st.selfID.get.getNodeID == sender.getNodeID

        isSenderSelf match {
          case true => false // 必要無し
          case false =>
            val isPredEmpty = st.pred.isEmpty
            val isSaidNodeBetter = (st.pred map {
              pred => {
                sender.belongs_between(pred).and(st.selfID.get) ||
                  st.selfID.get == pred /*||
                  (
                    !st.succList.nodes.empty
                && st.succList.nearestSuccessorWithoutSelf(st.selfID.get).flatMap(ida => Some(ida == pred)).getOrElse(false)
                    )
                    */
              }
            }) | false // pred = Noneの場合も考慮
          val isPredDead = !new successorStabilizationFactory().isPredLiving(st) // 副作用あり
            isPredEmpty ∨ isSaidNodeBetter ∨ isPredDead
        }
      } | false

    val execute: Boolean => (Agent[ChordState], idAddress) => Unit = {
      case true => (agt: Agent[ChordState], addr: idAddress) => {
        context.system.log.info("going to replace predecessor.")

        // 以前のpredがあればアンウォッチする
        agt().pred map (ida => context.unwatch(ida.actorref))

        // predを変更する
        agt send {
          _.copy(pred = addr.some)
        }

        // succ=selfの場合、succも同時変更する
        if (agt().succList.nearestSuccessor(agt().selfID.get) == agt().selfID.get) {
          agt send {
            _.copy(succList = NodeList(List(addr)))
          }
        }
        agt().stabilizer ! StartStabilize
        context.watch(addr.actorref)
      }

      case false => (_: Agent[ChordState], _: idAddress) => // do nothing
    }

    atomic {
      implicit txn => (check >>> execute)(state())(state, sender)
    }
  }

}
