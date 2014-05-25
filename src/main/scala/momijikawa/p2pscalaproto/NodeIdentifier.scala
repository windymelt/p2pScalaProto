package momijikawa.p2pscalaproto

import networking.NodeMessenger

trait NodeIdentifier extends TnodeID {
  type URI
  val id: nodeID
  val uri: URI
  override def equals(that: Any): Boolean
  def serialize: String
  def getMessenger: NodeMessenger
  @deprecated def getTransmitter: NodeMessenger
}
