package momijikawa.p2pscalaproto.stabilizer

import momijikawa.p2pscalaproto.ChordState

class StabilizeDeadStrategyRecovering(state: ChordState) extends StabilizeDeadStrategy(state) {
  /**
   * このノードに最も近いSuccessorをリストから除外します。Successorが死んでいる場合などに呼ばれます。
   * @return 更新されたノードの状態。
   */
  private def recoverSuccList(): ChordState = {
    val newState = state.copy(succList = state.succList.killNearest(state.selfID.get))
    joinNetwork(newState, newState.succList.nearestSuccessor(newState.selfID.get))._1 // TODO: predも使用できる
  }

  override def stabilize(): ChordState = {
    recoverSuccList()
  }
}
