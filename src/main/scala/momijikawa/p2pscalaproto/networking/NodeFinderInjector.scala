package momijikawa.p2pscalaproto.networking

import momijikawa.p2pscalaproto.{ NodeFinder, ChordState, TnodeID, idAddress }
import akka.agent.Agent
import akka.actor.ActorContext
import momijikawa.p2pscalaproto.messages.{ FindNode, NodeIdentifierMessage }

/**
 * 実際にChordが利用するノード検索の実装。
 * @param objective 検索目標のノードID。
 * @param state このノードの状態の[[akka.agent.Agent]]。
 * @param context 送信者の特定に必要な情報。
 */
class NodeFinderInjector(objective: TnodeID, state: Agent[ChordState])(implicit val context: ActorContext) {

  private def reply(message: NodeIdentifierMessage) = context.sender ! message
  private val myTerritory = () ⇒ {
    reply(NodeIdentifierMessage(identifier = state().selfID))
  }
  private val nextNode = () ⇒ {
    // 相手がNoneを返した場合も素直にNoneを返す
    reply(NodeIdentifierMessage(Some(state().succList.nodes.head)))
  }
  private val forward = () ⇒ {
    state().fingerList.closestPrecedingNode(objective)(state().selfID.get) match {
      case node @ idAddress(_, _) ⇒ node.actorref.forward(FindNode(objective.getBase64))
      case _                      ⇒ throw new Exception("Cannot forward message when the object which is not idAddress is used.")
    }
  }
  def judge() = new NodeFinder(objective, state().selfID.get, state().succList.nodes.head, myTerritory, nextNode, forward).judge
}
