package momijikawa.p2pscalaproto.stabilizer

import momijikawa.p2pscalaproto.ChordState

class StabilizeAloneStrategy(state: ChordState) extends StabilizeStrategy {
  def stabilize(): ChordState = state
}
