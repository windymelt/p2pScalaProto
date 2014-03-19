package momijikawa.p2pscalaproto

class NodeFinder[A](objective: TnodeID, self: TnodeID, successor: TnodeID, myTerritory: () => A, onHandling: () => A, onForwarding: () => A) {
  def judge: A = {
    val isNodeAlone = successor == self
    if (isNodeAlone || objective == self) myTerritory() else {
      if (objective belongs_between self and successor) onHandling() else onForwarding()
    }
  }
}

import akka.agent.Agent
import akka.actor.ActorContext

class NodeFinderInjector(objective: TnodeID, state: Agent[ChordState])(implicit val context: ActorContext) {

  private def reply(message: IdAddressMessage) = context.sender ! message
  private val myTerritory = () => {
    reply(IdAddressMessage(idaddress = state().selfID))
  }
  private val nextNode = () => {
    // 相手がNoneを返した場合も素直にNoneを返す
    reply(IdAddressMessage(Some(state().succList.nodes.head)))
  }
  private val forward = () => {
    state().fingerList.closestPrecedingNode(objective)(state().selfID.get) match {
      case node => node.actorref.forward(FindNode(objective.getBase64))
    }
  }
  def judge() = new NodeFinder(objective, state().selfID.get, state().succList.nodes.head, myTerritory, nextNode, forward).judge
}