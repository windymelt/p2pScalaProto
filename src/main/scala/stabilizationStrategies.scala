package momijikawa.p2pscalaproto

// TODO: Stateを利用する処理を一掃し、Agentに変更せよ。次いで簡潔にできる処理をStateを利用してリファクタせよ。
// TODO: 透過性を基準に関数を抽出してみよう

import scalaz._
import Scalaz._
import scalaz.Ordering.GT
import Utility.Utility.pass
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.agent.Agent
import scala.concurrent.stm._

/**
 * ノードの安定化に用いるパターンのトレイト。
 */
trait stabilizationStrategy {
  type StateAgent = Agent[ChordState]
  val agent: StateAgent

  def doStrategy(): stabilizationStrategy

  def before(): Unit = {
    //println(this.toString)
  }
}

/**
 * Successorが死んでるとき
 * Successorとの通信ができないときのパターンです。まずSuccessorのリストから次のSuccessor候補を探し出し接続しようとし、
 * 失敗した場合はPredecessorとの接続を試行しますが、失敗した場合は安定化処理を中止します。
 * @param agent [[momijikawa.p2pscalaproto.ChordState]]の[[akka.agent.Agent]]
 */
case class SuccDeadStrategy(agent: Agent[ChordState]) extends stabilizationStrategy {

  /**
   * Successorが死んでいるものとみなし、Successorのリストを再構築します。
   * @return ストラテジを返します。
   */
  def doStrategy() = atomic {
    implicit txn =>
      super.before()
      agent().succList.nodes.size ?|? 1 match {
        case GT => recoverSuccList() // SuccListに余裕があるとき
        case _ => joinPred(agent)
      }
      this
  }

  def joinPred(agent: StateAgent): idAddress = {
    // TODO: 簡潔にリファクタする
    val joinedResult =
      for {
        pred <- agent().pred
        joinedTrying <- ChordState.joinA(pred, agent)
      } yield joinedTrying

    joinedResult | bunkruptNode(agent)
  }

  def bunkruptNode(agent: StateAgent) = {
    agent.await(30 second).stabilizer ! StopStabilize
    agent send (st => st.copy(succList = NodeList(List[idAddress](st.selfID.get)), pred = None))
    agent().selfID.get
  }

  def recoverSuccList() = {
    agent.await(30 second)
    agent send (st => st.copy(succList = st.succList.killNearest(st.selfID.get)))
    ChordState.joinA(agent().succList.nearestSuccessor(agent().selfID.get), agent)
  }

}

case class PreSuccDeadStrategy(agent: Agent[ChordState]) extends stabilizationStrategy {
  def doStrategy() = {
    super.before()
    agent().succList.nearestSuccessor(agent().selfID.get).getClient(agent().selfID.get).amIPredecessor()
    this
  }
}

/**
 * 自分がSuccessorの正当なPredecessorである場合のストラテジです。
 * Successorに対してPredecessorを確認し、変更すべきことを通知します。
 * @param agent [[momijikawa.p2pscalaproto.ChordState]]の[[akka.agent.Agent]]
 */
case class RightStrategy(agent: Agent[ChordState]) extends stabilizationStrategy {
  def doStrategy() = {
    super.before()
    agent().succList.nearestSuccessor(agent().selfID.get).getClient(agent().selfID.get).amIPredecessor()
    this
  }
}

/**
 * 自分がSuccessorの正当なPredecessorではない場合のストラテジです。
 * SuccessorをSuccessorのPredecessorに変更します。SuccessorのPredecessorが利用できないときは、[[momijikawa.p2pscalaproto.PreSuccDeadStrategy]]に処理を渡します。
 * @param agent [[momijikawa.p2pscalaproto.ChordState]]の[[akka.agent.Agent]]
 */
case class GaucheStrategy(agent: Agent[ChordState]) extends stabilizationStrategy {
  def doStrategy() = atomic {
    implicit txn =>
      super.before()
      val preSucc = Await.result(agent().succList.nearestSuccessor(agent().selfID.get).getClient(agent().selfID.get).yourPredecessor, 10 second).idaddress

      preSucc match {
        case Some(v) =>
          val renewedcs: State[ChordState, ChordState] = for {
            _ <- modify[ChordState](_.copy(succList = NodeList(List[idAddress](v))))
            newcs <- get[ChordState]
            _ <- pass(newcs.succList.nearestSuccessor(newcs.selfID.get).getClient(newcs.selfID.get).amIPredecessor())
          } yield newcs

          agent send renewedcs.run(agent())._1
          this
        case None =>
          PreSuccDeadStrategy(agent).doStrategy()
      }
  }
}

/**
 * 通常時のストラテジです。
 * Successorを増やし、データの異動が必要な場合は転送します。
 * @param agent [[momijikawa.p2pscalaproto.ChordState]]の[[akka.agent.Agent]]
 */
case class NormalStrategy(agent: Agent[ChordState]) extends stabilizationStrategy {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future

  def doStrategy() = {
    super.before()
    Await.result(agent() |> increaseSuccessor >>> immigrateData, 50 second)
    this
  }

  // TODO: Successorを増やす処理を実装すること
  val increaseSuccessor = (cs: ChordState) => {
    // TODO: nodelistをクラスとして分離すべき
    val head = cs.succList.nearestSuccessor(id_self = cs.selfID.get) // assuming not null
    val newSuccList: Option[List[idAddress]] = // monad transformer使いたいけど使い方がわからん
      for {
        selfid <- cs.selfID
        succ <- Await.result(head.getClient(selfid).yourSuccessor, 10 second).idaddress
        succc <- Await.result(succ.getClient(selfid).yourSuccessor, 10 second).idaddress
        succcc <- Await.result(succc.getClient(selfid).yourSuccessor, 10 second).idaddress
        succccc <- Await.result(succcc.getClient(selfid).yourSuccessor, 10 second).idaddress
      } yield List(head, succ, succc, succcc, succccc)

    newSuccList match {
      case Some(lis) => cs.copy(succList = NodeList(lis))
      case None => cs
    }
  }

  val immigrateData = (cs: ChordState) => Future {
    val self = cs.selfID.get
    val dataShouldBeMoved = cs.dataholder.filterKeys {
      (key: Seq[Byte]) =>
        nodeID(key.toArray).belongs_between(self).and(cs.succList.nearestSuccessor(self))
        !TnodeID.belongs(nodeID(key.toArray), self, NodeList(cs.succList.nodes.list ++ cs.fingerList.nodes.list).nearestNeighbor(nodeID(key.toArray), self))
    }
    val movedNode = dataShouldBeMoved map {
      (f: (Seq[Byte], KVSData)) =>
        (f._1, Await.result(self.getClient(self).findNode(nodeID(f._1.toArray)), 50 second))
    }
    val moving = movedNode map {
      (xs: (Seq[Byte], IdAddressMessage)) => xs._2.idaddress.get.getClient(self).setChunk(xs._1, dataShouldBeMoved(xs._1))
    }
    moving.toList.sequence match {
      case Some(_) => cs.copy(dataholder = cs.dataholder -- dataShouldBeMoved.keys)
      case None => cs
    }
  }
}
