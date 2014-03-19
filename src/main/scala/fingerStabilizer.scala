package momijikawa.p2pscalaproto

import scalaz._
import akka.agent.Agent
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

// TODO: fix description on release
/**
 * FingerTableの安定化を行います。
 * このオブジェクトは直接動作を行いません。[[scalaz.State]]を返し、実際の動作は他所で行います。
 */
class FingerStabilizer(watcher: NodeWatcher, agent: Agent[ChordState]) {

  // TODO: fix description on release
  /**
   * 安定化します。[[momijikawa.p2pscalaproto.ChordState]]を渡すと[[momijikawa.p2pscalaproto.ChordState.fingerList]]中のランダムなノードを最適なノードに置き換えます。
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

  def stabilize(): Unit = stabilizeOn(generateRandomIndex)

  def generateRandomIndex = {
    util.Random.nextInt(agent().fingerList.nodes.size - 1) + 1
  }

  private def alterStatus(x: ChordState) = Await.result(agent.alter(x), 5 seconds)

  def fetchNode(idx: Int): Future[Option[idAddress]] = {
    val indexAsNodeID = TnodeID.fingerIdx2NodeID(idx)(agent().selfID.get.asNodeID)
    agent().selfID.get.getTransmitter.findNode(indexAsNodeID) map { idam => idam.idaddress }
  }

  private def fixList(index: Int, node: Option[idAddress], fingerList: NodeList): NodeList = {
    node match {
      case Some(exactNode) => fingerList.patch(index, exactNode)
      case None => fingerList
    }
  }

  private def recreateState(fingerList: NodeList): ChordState = agent().copy(fingerList = fingerList)

  private def watch(node: idAddress): Unit = watcher.watch(node.a)

  private def unwatch(node: idAddress): Unit = watcher.unwatch(node.a)
}
