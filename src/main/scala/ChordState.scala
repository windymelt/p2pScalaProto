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
                       stabilizer: ActorRef)

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
  def dataPut(key: Seq[Byte], value: KVSData, state: Agent[ChordState]): Option[Seq[Byte]] =
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
  def joinA(target: idAddress, state: Agent[ChordState]): Option[idAddress] = atomic {
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

  /**
   * あるノードIDを管轄するノードを検索します。
   * 自分とノードIDが同じであるか、もしくは自分が担当のノードであるときは自分を示す[[momijikawa.p2pscalaproto.IdAddressMessage]]を発信者に送り返します。
   * そうでないときは、最も近いと推定されるノードに問い合わせを転送します。
   * @param csa 自分のノードの[[momijikawa.p2pscalaproto.ChordState]]の[[akka.agent.Agent]]
   * @param id_query 検索したいノードID
   * @param context 発信者が分かる文脈情報
   */
  def findNodeCoreS(csa: Agent[ChordState], id_query: TnodeID)(implicit context: ActorContext) = atomic {
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
  val yourSuccessorCore: (ChordState) => Option[idAddress] = (cs: ChordState) => cs.selfID map {
    selfID => cs.succList.nearestSuccessor(selfID)
  } //cs.succList.last.some

  /**
   * 自分のPredecessorを返します。
   * @param cs 自分のノードの状態。
   * @param context
   * @return Predecessor
   */
  def yourPredecessorCore(cs: ChordState)(implicit context: ActorContext): Option[idAddress] = {
    context.system.log.debug("YourPredecessorCore")
    cs.stabilizer ! StartStabilize
    cs.pred
  }

  /**
   * 現在のPredecessorと比較し、最適な状態になるべく調整します。
   * 渡された[[momijikawa.p2pscalaproto.idAddress]]と現在のPredecessorを比較し、原則として、より近い側をPredecessorとして決め、ノードの状態を更新します。
   * また、渡されたものと自分が同じノードであるときは変更しません。
   * また、現在のPredecessorが利用できないときには渡された[[momijikawa.p2pscalaproto.idAddress]]を受け入れます。
   * @param address 比較対象のPredecessor。
   * @param state 自分のノードの状態。
   */
  def checkPredecessor(address: idAddress, state: Agent[ChordState])(implicit context: ActorContext) = {
    val check: (ChordState) => Boolean =
      (st) => allCatch.opt {
        println("checking my predecessor")
        // when pred is self?
        val selfIsNotSaid = !(st.selfID.get.getNodeID == address.getNodeID)
        val predIsNone = st.pred.isEmpty
        val saidWorths = (st.pred map {
          pred => address.belongs_between(pred).and(st.selfID.get)
        }) | false // pred = Noneの場合も考慮
        val predIsDead = !new successorStabilizationFactory().checkPredLiving(st) // 副作用あり
        println("checkpredecessor - P fetched")
        selfIsNotSaid ∧ (predIsNone ∨ saidWorths ∨ predIsDead)
      } | false

    val execute: Boolean => (Agent[ChordState], idAddress) => Unit = {
      case true => (agt: Agent[ChordState], addr: idAddress) => {
        println("going to replace predecessor.")
        agt().pred map (ida => context.unwatch(ida.actorref))
        agt send {
          _.copy(pred = addr.some)
        }
        agt().stabilizer ! StartStabilize
        context.watch(addr.actorref)
      }

      case false => (_: Agent[ChordState], _: idAddress) => // do nothing
    }

    atomic {
      implicit txn => (check >>> execute)(state())(state, address)
    }
  }

}
