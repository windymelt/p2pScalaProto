package momijikawa.p2pscalaproto.stabilizer

import momijikawa.p2pscalaproto.ChordState

trait StabilizeStrategy {
  def stabilize(): ChordState
}
