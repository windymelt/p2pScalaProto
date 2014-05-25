package momijikawa.p2pscalaproto.stabilizer

import momijikawa.p2pscalaproto.{ NodeIdentifier, ChordState }

class StabilizeDeadStrategy(state: ChordState) extends StabilizeStrategy {

  import scalaz._
  import Scalaz._
  import scalaz.Ordering.GT

  /**
   * Chordネットワークに接続します。
   * @param state このノードの状態。
   * @param ida 接続先の踏み台ノード。
   * @return 新たなSuccessorが[[scala.Option]]で返ります。
   */
  protected def joinNetwork(state: ChordState, ida: NodeIdentifier): (ChordState, Option[NodeIdentifier]) = ChordState.joinNetworkS(ida).run(state)

  def stabilize(): ChordState = {
    state.succList.nodes.size ?|? 1 match {
      case GT ⇒ new StabilizeDeadStrategyRecovering(state).stabilize // SuccListに余裕があるとき
      case _  ⇒ new StabilizeDeadStrategyJoiningPred(state).stabilize
    }
  }
}
