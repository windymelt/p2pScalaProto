package momijikawa.p2pscalaproto

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.agent.Agent
import scala.concurrent.stm._
import akka.actor.ActorContext

class successorStabilizationFactory(implicit context: ActorContext) {

  /**
   * [[momijikawa.p2pscalaproto.ChordState]]から自動的に戦略を生成します。
   * このメソッドはトランザクショナルです。
   */
  def autoGenerate(agt: Agent[ChordState]) = atomic {
    implicit txn =>
      val st = Await.result(agt.future, 30 seconds)
      generate(checkSuccLiving(st), checkPreSuccLiving(st), checkRightness(st), checkConsistentness(st), agt)
  }

  /**
   * 条件を元に[[momijikawa.p2pscalaproto.stabilizationStrategy]]を返します。
   * @param succliving Successorが生きているかどうか
   * @param presuccliving SuccessorのPredecessorが生きているか
   * @param rightness このノードがSuccessorの正当なPredecessorか
   * @param consistentness SuccessorとPredecessorとの関係に矛盾がないか
   * @return 実行すべき戦略。
   */
  def generate(succliving: Boolean, presuccliving: Boolean, rightness: Boolean, consistentness: Boolean, agent: Agent[ChordState]) = {
    println("generator working")
    if (!succliving) {
      // Successorが死んでる
      SuccDeadStrategy(agent)
    } else if (!presuccliving) {
      // SuccessorのPredecessorが死んでる
      PreSuccDeadStrategy(agent)
    } else if (consistentness) {
      // 想定されるうえでふつうの状況
      NormalStrategy(agent)
    } else if (rightness) {
      // Successorとpre-Successorとの間に割り込む場合
      RightStrategy(agent)
    } else {
      // Successorとpre-successorとの間に入れない場合
      GaucheStrategy(agent)
    }
  }

  /**
   * Successorが生きているかどうかを返します。
   * @param state ChordState
   * @return 生きて（いるtrue/いないfalse）
   */
  def checkSuccLiving(state: ChordState): Boolean = {
    try {
      state.succList.nearestSuccessor(id_self = state.selfID.get) match {
        case nrst if nrst.getNodeID == state.selfID.get.getNodeID => true
        case nrst if nrst.a.isTerminated => false
        case nrst => nrst.getClient(state.selfID.get).checkLiving
      }
    } catch {
      case _: Exception => false
    }
  }

  /**
   * SuccessorのPredecessorが生きているかどうかを返します。
   * @return { @see #checkSuccLiving}と同じです。
   */
  def checkPreSuccLiving(state: ChordState): Boolean = {
    try {
      val cli_next: Transmitter = state.succList.nearestSuccessor(id_self = state.selfID.get).getClient(state.selfID.get)
      Await.result(cli_next.yourPredecessor, 10 second).idaddress match {
        case None => false
        case Some(preNext) if preNext.getNodeID == state.selfID.get.getNodeID => true
        case Some(preNext) if preNext.a.isTerminated => false
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
      case Some(v) =>
        v match {
          case ida if ida.getNodeID == state.selfID.get.getNodeID => true
          case ida if ida.a.isTerminated => false
          case ida => ida.getClient(state.selfID.get).checkLiving
        }
      case None => false
    }
  }

  /**
   * このノードがSuccessorとSuccessorのPredecessorの間に入れるかどうかを返します。
   * @return 入れるならtrueを、入れないもしくは自分のSuccessorが自分自身の場合falseを返します。
   */
  def checkRightness(state: ChordState): Boolean = {
    val Succ: idAddress = state.succList.nearestSuccessor(id_self = state.selfID.get)

    state.selfID.get.getNodeID == Succ.getNodeID match {
      case true => false // 自分が孤独状態ならすぐに譲る
      case false =>
        Await.result(Succ.getClient(state.selfID.get).yourPredecessor, 10 second).idaddress match {
          case None => true
          case Some(preSucc) => state.selfID.get.belongs_between(preSucc).and(Succ)
        }
    }
  }

  /**
   * SuccessorのPredecessorが自分を参照しているかどうかを返します。
   * @return 参照している場合はtrueを、それ以外の場合はfalseを返します。
   */
  def checkConsistentness(state: ChordState): Boolean = {
    val preSucc: Option[idAddress] = Await.result(state.succList.nearestSuccessor(id_self = state.selfID.get).getClient(state.selfID.get).yourPredecessor, 10 second).idaddress
    preSucc match {
      case None => false
      case Some(pSucc) => state.selfID.get.getNodeID == pSucc.getNodeID
    }
  }
}
