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
import momijikawa.p2pscalaproto.stabilizer.StabilizerController

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
    selfID: Option[NodeIdentifier],
    succList: NodeList,
    fingerList: NodeList,
    pred: Option[NodeIdentifier],
    dataholder: HashMap[Seq[Byte], KVSData],
    stabilizer: StabilizerController) {
  def dropNode(a: ActorRef): ChordState = {
    this.copy(selfID, succList.remove(a), fingerList.replace(a, selfID.get), pred.flatMap((i) ⇒ if (i.uri == a) None else i.some), dataholder, stabilizer)
  }

  /**
   * 現在分かる範囲における、自分に最も近いSuccessorを返します。
   */
  def getNearestSuccessor: Option[NodeIdentifier] = selfID map {
    selfID ⇒ succList.nearestSuccessor(selfID)
  }

  /**
   * 自分のPredecessorを返します。
   * @param context
   * @return Predecessor
   */
  def getPredecessor()(implicit context: ActorContext): Option[NodeIdentifier] = {
    context.system.log.debug("YourPredecessorCore")
    // ここでもsuccの自動変更が必要？
    stabilizer.start()
    pred
  }
}

/**
 * [[momijikawa.p2pscalaproto.ChordState]]を変更するための静的なメソッド群。
 */
object ChordState {
  type dataMap = HashMap[Seq[Byte], Seq[Byte]]
  type idListT = List[NodeIdentifier]

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
  // MOVED: StateAgtController
  def putDataToNode(key: Seq[Byte], value: KVSData, state: Agent[ChordState]): Option[Seq[Byte]] =
    atomic {
      implicit txn ⇒
        allCatch opt {
          state send {
            (st) ⇒ st.copy(dataholder = st.dataholder + ((key, value)))
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
  def joinNetwork(target: NodeIdentifier, state: Agent[ChordState]): Option[NodeIdentifier] = atomic {
    implicit txn ⇒
      for {
        selfID ← state().selfID
        mightBeNewSuccessor ← (allCatch opt Await.result(target.getTransmitter.findNode(selfID), 10 second).identifier).flatten[NodeIdentifier]
        _ ← (state send {
          _.copy(succList = NodeList(List[NodeIdentifier](mightBeNewSuccessor).toNel.get), pred = None)
        }).some
        _ ← (state().stabilizer.start()).some
      } yield mightBeNewSuccessor
  }

  // 安定化処理に使われている。安定化処理中はアトミックな変更を必要とするので逐次ステートを変更できない。
  // =>ではトランザクション処理を利用すればよいのでは？
  // =>Agentではトランザクションが使えない。別のAgentを生成して後から本流に投げればよいのでは？
  val joinNetworkS: NodeIdentifier ⇒ State[ChordState, Option[NodeIdentifier]] = (target: NodeIdentifier) ⇒
    State[ChordState, Option[NodeIdentifier]] {
      cs ⇒
        {
          for {
            sid ← cs.selfID
            newSucc ← (allCatch opt findSelf(target, sid)).flatten[NodeIdentifier]
            newState ← regenerateState(cs, newSucc).some
            _ ← startStabilize(newState).some
          } yield (newState, newSucc.some)
        } | (cs, None)
    }

  private def findSelf(target: NodeIdentifier, sid: NodeIdentifier): Option[NodeIdentifier] = Await.result(target.getTransmitter.findNode(sid), 10 second).identifier

  private def regenerateState(cs: ChordState, newSucc: NodeIdentifier): ChordState = cs.copy(succList = NodeList(List[NodeIdentifier](newSucc).toNel.get), pred = None)

  private def startStabilize(cs: ChordState): Unit = cs.stabilizer.start()

}
