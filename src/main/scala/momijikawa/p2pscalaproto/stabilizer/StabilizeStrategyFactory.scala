package momijikawa.p2pscalaproto.stabilizer
import momijikawa.p2pscalaproto.ChordState

class StabilizeStrategyFactory(state: ChordState) {
  def select: StabilizeStrategy = {
    val selfID = state.selfID.get
    val succID = state.succList.nearestSuccessor(selfID)
    if (selfID == succID) {
      new StabilizeAloneStrategy(state)
    } else {
      if (succID.getMessenger.checkLiving) {
        new StabilizeLivingStrategy(state)
      } else {
        new StabilizeDeadStrategy(state)
      }
    }
  }
}
