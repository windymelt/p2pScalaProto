package momijikawa.p2pscalaproto

import scalaz._
import akka.agent.Agent
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * FingerTableの安定化を行います。
 * [[akka.agent.Agent]]を受け取り、FingerTableを更新します。
 */
class FingerStabilizer(watcher: NodeWatcher, agent: Agent[ChordState]) {

  /**
   * FingerTableのうち所与のインデックスのものを更新します。
   * @param indexToUpdate 更新するインデックス。
   */
  def stabilizeOn(indexToUpdate: Int): Unit = {
    // 1 to size-1 (except for 0: 2^0)
    require(indexToUpdate >= 0 && indexToUpdate < agent().fingerList.nodes.size)
    unwatch(agent().fingerList(indexToUpdate))
    val newIdAddressFuture: Future[Option[idAddress]] = fetchNode(indexToUpdate)
    newIdAddressFuture map {
      newNode =>
        newNode foreach watch
        val newList = fixList(indexToUpdate, newNode, agent().fingerList)
        val newStatus = recreateState(newList)
        alterStatus(newStatus)
    }
  }

  /**
   * ランダムにFingerTableを更新します。
   */
  def stabilize(): Unit = stabilizeOn(generateRandomIndex)

  /**
   * FingerTableの更新に使うランダムなインデックスを生成します。
   * @return 0からFingerTableのサイズ-1までのランダムな自然数。
   */
  def generateRandomIndex = {
    util.Random.nextInt(agent().fingerList.nodes.size - 1) + 1
  }

  /**
   * ノードの状態を更新します。
   * @param x 状態オブジェクト。
   */
  private def alterStatus(x: ChordState): Unit = Await.result(agent.alter(x), 5 seconds)

  /**
   * FingerTableのインデックスからノードIDを生成し、最も近いノードを検索して返します。
   * @param idx FingerTableのインデックス。
   * @return 最もインデックスに該当するFingerTableの要素としてふさわしいノード。
   */
  def fetchNode(idx: Int): Future[Option[idAddress]] = {
    val indexAsNodeID = TnodeID.fingerIdx2NodeID(idx)(agent().selfID.get.asNodeID)
    agent().selfID.get.getTransmitter.findNode(indexAsNodeID) map { idam => idam.idaddress }
  }

  /**
   * 所与のインデックスと要素で[[momijikawa.p2pscalaproto.NodeList]]を更新します。
   * @param index 書き換えるインデックス。
   * @param node 要素。
   * @param fingerList 更新されるFingerTable。
   * @return 更新されたFingerTable。
   */
  private def fixList(index: Int, node: Option[idAddress], fingerList: NodeList): NodeList = {
    node match {
      case Some(exactNode) => fingerList.patch(index, exactNode)
      case None => fingerList
    }
  }

  /**
   * 所与のFingerTableからノードの状態を再生成します。
   * @param fingerList 差し替えるFingerTable。
   * @return 更新されたlFingerTable。
   */
  private def recreateState(fingerList: NodeList): ChordState = agent().copy(fingerList = fingerList)

  /**
   * ノードを監視します。監視しておくとノードが死んだ場合に自動的に登録が解除されます。
   * @param node 監視対象のノード。
   */
  private def watch(node: idAddress): Unit = watcher.watch(node.a)

  /**
   * ノードの監視を解除します。
   * @param node 監視を解除したいノード。
   */
  private def unwatch(node: idAddress): Unit = watcher.unwatch(node.a)
}
