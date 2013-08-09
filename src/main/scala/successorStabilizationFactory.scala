package momijikawa.p2pscalaproto

/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/07/24
 * Time: 20:23
 * To change this template use File | Settings | File Templates.
 */
object successorStabilizationFactory {
  def autoGenerate(st: ChordState) = {
    generate(checkSuccLiving(st), checkPreSuccLiving(st), checkRightness(st), checkConsistentness(st))
  }

  def generate(succliving: Boolean, presuccliving: Boolean, rightness: Boolean, consistentness: Boolean) = {
    if (!succliving) {
      stabilizationSuccDeadStrategy
    } else if (!presuccliving) {
      stabilizationPreSuccDeadStrategy
    } else if (consistentness) {
      stabilizationOKStrategy
    } else if (rightness) {
      stabilizationRightStrategy
    } else {
      stabilizationGaucheStrategy
    }
  }

  def checkSuccLiving(state: ChordState): Boolean = {
    try {
      state.succList.last.getClient(state.selfID.get).checkLiving
    } catch {
      case _: Exception =>
        return false
    }
  }

  /**
   * SuccessorのPredecessorが生きているかどうかを返します。
   * @return { @see #checkSuccLiving}と同じです。
   */
  def checkPreSuccLiving(state: ChordState): Boolean = {
    try {
      val cli_next: Transmitter = state.succList.last.getClient(state.selfID.get)

      cli_next.yourPredecessor match {
        case None => false
        case Some(preNext) => preNext.getClient(state.selfID.get).checkLiving
      }

    } catch {
      case _: Exception => false
    }
  }

  /**
   * Predecessorが生きているかどうかを返します。
   * @return { @see #checkSuccLiving}と同じです。
   */
  def checkPredLiving(state: ChordState): Boolean = {
    state.pred match {
      case Some(v) => v.getClient(state.selfID.get).checkLiving
      case None => false
    }
  }

  /**
   * このノードがSuccessorとSuccessorのPredecessorの間に入れるかどうかを返します。
   * @return 入れるならtrueを、入れないもしくは自分のSuccessorが自分自身の場合falseを返します。
   */
  def checkRightness(state: ChordState): Boolean = {
    val Succ: TnodeID = state.succList.last

    if (state.selfID.get.idVal.deep.equals(Succ.idVal.deep)) {
      return false; // 自分が孤独状態ならすぐに譲る
    }

    state.succList.last.getClient(state.selfID.get).yourPredecessor match {
      case None => true
      case Some(preSucc) => TnodeID.belongs(state.selfID.get, preSucc, Succ)
    }
  }

  /**
   * SuccessorのPredecessorが自分を参照しているかどうかを返します。
   * @return 参照している場合はtrueを、それ以外の場合はfalseを返します。
   */
  def checkConsistentness(state: ChordState): Boolean = {

    val preSucc: Option[idAddress] = state.succList.last.getClient(state.selfID.get).yourPredecessor
    preSucc match {
      case None =>
        System.out.println("preSucc is null")
        false
      case Some(pred) =>
        System.out.println("presucc is: " + pred.getBase64)
        state.selfID.get.getBase64.equals(pred.getBase64)
    }


  }
}
