package momijikawa.p2pscalaproto.stabilizer

import momijikawa.p2pscalaproto._
import scala.concurrent.Await
import momijikawa.p2pscalaproto.messages.NodeIdentifierMessage

class StabilizeLivingStrategyNotifying(state: ChordState, pred_of_succ: NodeIdentifier) extends StabilizeLivingStrategy(state) {
  import scalaz._
  import Scalaz._
  import scala.concurrent.duration._

  /**
   * Successorのリストを多重化します。
   */
  private val increaseSuccessor: ChordState ⇒ ChordState = (state: ChordState) ⇒ {
    def genList(succ: NodeIdentifier)(operation: NodeIdentifier ⇒ Option[NodeIdentifier])(length: Int): List[NodeIdentifier] = succ :: unfold(succ)(_ |> operation map (_.squared)).take(length).toList

    val newSuccList: List[NodeIdentifier] = genList(succID)(ida ⇒ Await.result[NodeIdentifierMessage](ida.getMessenger.yourSuccessor, 10 seconds).identifier >>= { node ⇒ if (node == selfID) None else node.some })(4)

    newSuccList match {
      case lis if lis.isEmpty ⇒
        state
      case lis ⇒
        state.copy(succList = NodeList(lis))
    }
  }

  override def stabilize(): ChordState = {
    notifyTo(selfID)(pred_of_succ)
    state |> increaseSuccessor >>> { (state) ⇒ new DataImmigrator(state).immigrate() }
  }
}
