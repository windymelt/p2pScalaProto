package momijikawa.p2pscalaproto.stabilizer

import momijikawa.p2pscalaproto._
import momijikawa.p2pscalaproto.nodeID
import momijikawa.p2pscalaproto.messages.NodeIdentifierMessage
import scala.concurrent.Await
import scala.concurrent.duration._
import scalaz._
import Scalaz._

class DataImmigrator(state: ChordState) {

  private def listUpToMove(cs: ChordState): Map[Seq[Byte], KVSData] = {
    cs.dataholder.filterKeys {
      (key: Seq[Byte]) ⇒
        nodeID(key.toArray).belongs_between(cs.selfID.get).and(cs.succList.nearestSuccessor(cs.selfID.get)) ||
          !nodeID(key.toArray).belongs_between(cs.selfID.get).and(NodeList(cs.succList.nodes.list ++ cs.fingerList.nodes.list).nearestNeighbor(nodeID(key.toArray), cs.selfID.get))
    }
  }

  private def findContainerNode(self: NodeIdentifier, map: Map[Seq[Byte], KVSData]): Map[Seq[Byte], NodeIdentifierMessage] = {
    map map {
      (f: (Seq[Byte], KVSData)) ⇒
        (f._1, Await.result(self.getTransmitter.findNode(nodeID(f._1.toArray)), 50 second))
    }
  }

  private val moveChunk = (toMove: Map[Seq[Byte], KVSData], self: NodeIdentifier) ⇒ (key: Seq[Byte], idam: NodeIdentifierMessage) ⇒ {
    idam.identifier.get.getMessenger.setChunk(key, toMove(key))
  }

  def immigrate(): ChordState = {
    val dataShouldBeMoved = listUpToMove(state)
    val recipientNode = findContainerNode(state.selfID.get, dataShouldBeMoved)
    val moving = recipientNode map moveChunk(dataShouldBeMoved, state.selfID.get).tupled

    moving.toList.sequence match {
      case Some(_) ⇒ state.copy(dataholder = state.dataholder -- dataShouldBeMoved.keys)
      case None    ⇒ state
    }
  }

}
