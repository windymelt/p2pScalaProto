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
  def autoGenerate(st: ChordState) = atomic {
    implicit txn =>
      generate(isSuccDead(st), isPreSuccDead(st), checkConsistentness(st), checkRightness(st))
  }

  /**
   * 条件を元に[[momijikawa.p2pscalaproto.stabilizationStrategy]]を返します。
   * @param succdead Successorが死んでいるかどうか
   * @param presuccdead SuccessorのPredecessorが生きているか
   * @param rightness このノードがSuccessorの正当なPredecessorか
   * @param consistentness SuccessorとPredecessorとの関係に矛盾がないか
   * @return 実行すべき戦略。
   */
  def generate(succdead: Boolean, presuccdead: Boolean, consistentness: Boolean, rightness: Boolean) = {
    println("generator working")
    if (succdead) {
      // Successorが死んでる
      SuccDeadStrategy
    } else if (presuccdead) {
      // SuccessorのPredecessorが死んでる
      PreSuccDeadStrategy
    } else if (consistentness) {
      // 想定されるうえでふつうの状況
      NormalStrategy
    } else if (rightness) {
      // Successorとpre-Successorとの間に割り込む場合
      RightStrategy
    } else {
      // Successorとpre-successorとの間に入れない場合
      // TODO: Gaucheになる基準がゆるすぎる。なぜかすぐにsuccessorがpredecessorに変異してしまう。
      GaucheStrategy
    }
  }

  /**
   * Successorが生きているかどうかを返します。
   * @param state ChordState
   * @return 生きて（いるtrue/いないfalse）
   */
  def isSuccDead(state: ChordState): Boolean = {
    try {
      state.succList.nearestSuccessor(id_self = state.selfID.get) match {
        case nrst if nrst.getNodeID == state.selfID.get.getNodeID => false
        case nrst => !nrst.getClient(state.selfID.get).checkLiving
      }
    } catch {
      case e: Exception =>
        context.system.log.warning(e.getLocalizedMessage)
        true
    }
  }

  /**
   * SuccessorのPredecessorが生きているかどうかを返します。
   * @return { @see #checkSuccLiving}と同じです。
   */
  def isPreSuccDead(state: ChordState): Boolean = {
    try {
      val cli_next: Transmitter = state.succList.nearestSuccessor(id_self = state.selfID.get).getClient(state.selfID.get)
      Await.result(cli_next.yourPredecessor, 10 second).idaddress match {
        case None => false
        case Some(preNext) if preNext.getNodeID == state.selfID.get.getNodeID => false
        case Some(preNext) => !preNext.getClient(state.selfID.get).checkLiving
      }
    } catch {
      case _: Exception => true
    }
  }

  /**
   * Predecessorが生きているかどうかを返します。
   * @return { @see #checkSuccLiving}と同じです。
   */
  def isPredLiving(state: ChordState): Boolean = {
    state.pred match {
      case Some(v) =>
        v match {
          case ida if ida.getNodeID == state.selfID.get.getNodeID => true
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
    val SuccNotMe: List[idAddress] = state.succList.nodes.list.filterNot(_ == state.selfID.get)
    SuccNotMe match {
      case lis if lis isEmpty => false
      case lis =>
        val Succ: idAddress = NodeList(lis).nearestSuccessor(id_self = state.selfID.get)
        // TODO: nearest == selfのとき？ (fixed)
        state.selfID.get.getNodeID == Succ.getNodeID match {
          case true => false // 自分が孤独状態ならすぐに譲る
          case false =>
            Await.result(Succ.getClient(state.selfID.get).yourPredecessor, 10 second).idaddress match {
              case None => true
              case Some(preSucc) => state.selfID.get.belongs_between(preSucc).and(Succ) || Succ == preSucc
            }
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
      case Some(pSucc) =>
        context.system.log.debug(s"checkConsistentness: (${state.selfID.get.getNodeID},${pSucc.getNodeID}})")
        state.selfID.get.getNodeID == pSucc.getNodeID
    }
  }
}
