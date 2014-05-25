package momijikawa.p2pscalaproto.stabilizer

import momijikawa.p2pscalaproto.{ NodeIdentifier, NodeList, ChordState }

class StabilizeLivingStrategyChangingSuccessor(state: ChordState, pred_of_succ: NodeIdentifier) extends StabilizeLivingStrategy(state) {
  override def stabilize(): ChordState = {
    notifyTo(selfID)(pred_of_succ)
    state.copy(succList = NodeList(List(pred_of_succ)))
  }
}
