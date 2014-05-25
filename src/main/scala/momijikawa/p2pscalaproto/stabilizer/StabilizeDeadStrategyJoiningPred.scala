package momijikawa.p2pscalaproto.stabilizer

import momijikawa.p2pscalaproto.{ NodeIdentifier, NodeList, ChordState }

class StabilizeDeadStrategyJoiningPred(state: ChordState) extends StabilizeDeadStrategy(state) {

  /**
   * ノードを停止させます。SuccessorもPredecessorも使えない場合に呼ばれます。ノードは起動直後と似た状態になります。
   * @param state このノードの状態。
   * @return 更新されたノードの状態。
   */
  private def bunkruptNode(state: ChordState): ChordState = {
    state.stabilizer.stop()
    state.copy(succList = NodeList(List[NodeIdentifier](state.selfID.get)), pred = None)
  }

  /**
   * Predecessorにjoinします。何らかの事情でSuccessorが利用できない場合に呼ばれます。
   */
  private val joinPred: (ChordState) ⇒ ChordState =
    (state: ChordState) ⇒ {
      val joinedResult =
        for {
          pred ← state.pred
          joinedTrying ← Some(joinNetwork(state, pred))
        } yield joinedTrying

      joinedResult match {
        case Some((c, None)) ⇒ bunkruptNode(c)
        case Some((c, _))    ⇒ c // do nothing
        case None            ⇒ state // do nothing
      }
    }

  override def stabilize(): ChordState = {
    joinPred(state)
  }
}
