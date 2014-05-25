package momijikawa.p2pscalaproto.stabilizer

import momijikawa.p2pscalaproto._
import scala.concurrent.Await
import scala.concurrent.duration._

class StabilizeLivingStrategy(state: ChordState) extends StabilizeStrategy {

  val selfID = state.selfID.get
  val succID = state.succList.nearestSuccessor(selfID)

  /**
   * 所定のノードのpredecessorを取得します。ブロッキング処理です。
   * @param node 取得先ノード。
   * @return nodeのpredecessorがOptionで返ります。
   */
  protected def predecessorOf(node: NodeIdentifier): Option[NodeIdentifier] = {
    Await.result(node.getMessenger.yourPredecessor, 20 seconds).identifier
  }

  /**
   * 自分が正当なPredecessorであることをSuccessorのノードに通告します。
   * @param self このノードの情報。
   * @param node 通告先のノード。
   */
  protected def notifyTo(self: NodeIdentifier)(node: NodeIdentifier): Unit = {
    node.getMessenger.amIPredecessor(self)
  }

  def stabilize(): ChordState = {
    predecessorOf(succID) match {
      case Some(pred_of_succ) ⇒
        if (pred_of_succ.belongs_between(selfID) and (succID)) {
          new StabilizeLivingStrategyChangingSuccessor(state, pred_of_succ).stabilize()
        } else {
          new StabilizeLivingStrategyNotifying(state, pred_of_succ).stabilize()
        }
      case None ⇒
        new StabilizeLivingStrategyNotifying(state, succID).stabilize()
    }
  }
}
