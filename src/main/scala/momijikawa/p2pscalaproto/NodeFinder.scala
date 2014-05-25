package momijikawa.p2pscalaproto

/**
 * ノードの検索を担うクラス。
 * @param objective 検索目標のノードID。
 * @param self このノードのID。
 * @param successor SuccessorのノードID。
 * @param myTerritory このノードが担当だった場合の処理。
 * @param onHandling Successorが担当である場合の処理。
 * @param onForwarding 処理を転送する場合の処理。
 * @tparam A 処理の返り値の型。
 */
class NodeFinder[A](objective: TnodeID, self: TnodeID, successor: TnodeID, myTerritory: () ⇒ A, onHandling: () ⇒ A, onForwarding: () ⇒ A) {
  def judge: A = {
    val isNodeAlone = successor == self
    if (isNodeAlone || objective == self) myTerritory() else {
      if (objective belongs_between self and successor) onHandling() else onForwarding()
    }
  }
}

import akka.agent.Agent
import akka.actor.ActorContext
import momijikawa.p2pscalaproto.messages.FindNode
import momijikawa.p2pscalaproto.messages.NodeIdentifierMessage

