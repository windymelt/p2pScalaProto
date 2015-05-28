package momijikawa.p2pscalaproto

// TODO: Stateを利用する処理を一掃し、Agentに変更せよ。次いで簡潔にできる処理をStateを利用してリファクタせよ。
// TODO: 透過性を基準に関数を抽出してみよう
// TODO: Pred死亡時の処理？

import scalaz._
import Scalaz._
import scalaz.Ordering.GT
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.agent.Agent
import scala.concurrent.stm._
import akka.actor.ActorContext
import LoggerLikeObject._
import WatchableObject._

/**
 * @deprecated
 * ノードの安定化に用いるパターンのトレイト。
 */
trait stabilizationStrategy {

  val doStrategy: State[ChordState, stabilizationStrategy]

  def before(): Unit = {
    //println(this.toString)
  }

  def tellAmIPredecessor(ida: idAddress, self: idAddress): Unit = ida.getTransmitter.amIPredecessor(self)
}

/**
 * Successorが死んでるとき
 * Successorとの通信ができないときのパターンです。まずSuccessorのリストから次のSuccessor候補を探し出し接続しようとし、
 * 失敗した場合はPredecessorとの接続を試行しますが、失敗した場合は安定化処理を中止します。
 */
case class SuccDeadStrategy(watcher: Watchable, logger: LoggerLike) extends stabilizationStrategy {

  /**
   * Successorが死んでいるものとみなし、Successorのリストを再構築します。
   * @return ストラテジを返します。
   */
  val doStrategy = State[ChordState, stabilizationStrategy] {
    cs =>
      atomic {
        implicit txn =>
          logger.info("INVOKED: SuccDeadStrategy")
          super.before()
          val newcs = cs.succList.nodes.size ?|? 1 match {
            case GT => recoverSuccList(cs) // SuccListに余裕があるとき
            case _ => joinPred(cs)
          }
          (newcs, this)
      }
  }

  val joinPred: (ChordState) => ChordState =
    (cs: ChordState) => {
      // TODO: 簡潔にリファクタする
      val joinedResult =
        for {
          pred <- cs.pred
          joinedTrying <- Some(joinNetwork(cs, pred))
        } yield joinedTrying

      joinedResult match {
        case Some((c, None)) => bunkruptNode(c)
        case Some((c, _)) => c // do nothing
        case None => cs // do nothing
      }
    }

  def bunkruptNode(cs: ChordState): ChordState = {
    stopStabilize(cs)
    cs.copy(succList = NodeList(List[idAddress](cs.selfID.get)), pred = None)
  }

  def recoverSuccList(cs: ChordState): ChordState = {
    watcher.unwatch(cs.succList.nearestSuccessor(cs.selfID.get).actorref)
    val newState = cs.copy(succList = cs.succList.killNearest(cs.selfID.get))
    joinNetwork(newState, newState.succList.nearestSuccessor(newState.selfID.get))._1 // TODO: predも使用できる
  }

  def stopStabilize(cs: ChordState) = cs.stabilizer.stop()

  def joinNetwork(cs: ChordState, ida: idAddress): (ChordState, Option[idAddress]) = ChordState.joinNetworkS(ida).run(cs)
}

case class PreSuccDeadStrategy(logger: LoggerLike) extends stabilizationStrategy {

  val doStrategy = State[ChordState, stabilizationStrategy] {
    cs =>
      logger.info("INVOKED: PreSuccDeadStrategy")
      super.before()
      cs.selfID >>= {
        self =>
          tellAmIPredecessor(cs.succList.nearestSuccessor(self), self).some
      }
      (cs, this)
  }

}

/**
 * 自分がSuccessorの正当なPredecessorである場合のストラテジです。
 * Successorに対してPredecessorを確認し、変更すべきことを通知します。
 */
case class RightStrategy(logger: LoggerLike) extends stabilizationStrategy {

  val doStrategy = State[ChordState, stabilizationStrategy] {
    cs =>
      logger.info("INVOKED: RightStrategy")
      super.before()
      cs.selfID >>= {
        self =>
          tellAmIPredecessor(cs.succList.nearestSuccessor(self), self).some
      }
      (cs, this)
  }
}

/**
 * 自分がSuccessorの正当なPredecessorではない場合のストラテジです。
 * SuccessorをSuccessorのPredecessorに変更します。SuccessorのPredecessorが利用できないときは、[[momijikawa.p2pscalaproto.PreSuccDeadStrategy]]に処理を渡します。
 */
case class GaucheStrategy(watcher: Watchable, logger: LoggerLike) extends stabilizationStrategy {

  val doStrategy = State[ChordState, stabilizationStrategy] {
    cs =>
      atomic {
        implicit txn =>
          logger.info("INVOKED: GaucheStrategy")
          super.before()
          val preSucc = getPreSucc(cs)

          preSucc match {
            case Some(v) =>
              val renewedcs: State[ChordState, ChordState] = for {
                _ <- gets[ChordState, Unit](st => st.succList.nodes.list.foreach(ida => watcher.unwatch(ida.actorref)))
                _ <- modify[ChordState](_.copy(succList = NodeList(List[idAddress](v))))
                newcs <- get[ChordState]
                _ <- Utility.Utility.pass(tellAmIPredecessor(newcs.succList.nearestSuccessor(newcs.selfID.get), newcs.selfID.get))
                _ <- gets[ChordState, Unit](st => st.succList.nodes.list.foreach(ida => watcher.watch(ida.actorref)))
              } yield newcs

              watcher.watch(v.actorref)
              (renewedcs.run(cs)._1, this)

            case None =>
              new PreSuccDeadStrategy(logger).doStrategy(cs)
          }
      }
  }

  def getPreSucc(cs: ChordState): Option[idAddress] = {
    Await.result(cs.succList.nearestSuccessor(cs.selfID.get).getTransmitter.yourPredecessor, 10 second).idaddress
  }

}

/**
 * 通常時のストラテジです。
 * Successorを増やし、データの異動が必要な場合は転送します。
 */
case class NormalStrategy(watcher: Watchable, logger: LoggerLike) extends stabilizationStrategy {

  import scala.concurrent.ExecutionContext.Implicits.global

  val doStrategy = State[ChordState, stabilizationStrategy] {
    cs =>
      super.before()
      val newcs = cs |> increaseSuccessor >>> watchRegistNodes >>> immigrateData

      (newcs, this)
  }

  val increaseSuccessor = (cs: ChordState) => {
    logger.debug("going to add successor")

    val succ = cs.succList.nearestSuccessor(id_self = cs.selfID.get) // assuming not null
    val genList = (suc: idAddress) => (operation: idAddress => Option[idAddress]) => (length: Int) =>
      unfold(suc)(_ |> operation map (_.squared)).take(length).toList
    val newSuccList: Option[List[idAddress]] = cs.selfID >>= {
      selfid =>
        genList(succ)(ida => Await.result[IdAddressMessage](ida.getTransmitter.yourSuccessor, 10 seconds).idaddress)(4).some
    }

    newSuccList match {
      case Some(lis) =>
        cs.copy(succList = NodeList(lis))
      case None => {
        logger.warning("failed to increase successor");
        cs
      }
    }
  }

  val watchRegistNodes = (cs: ChordState) => {
    cs.succList.nodes.list.foreach(ida => watcher.watch(ida.actorref))
    cs
  }

  val immigrateData = (cs: ChordState) => {
    cs.selfID >>= {
      self =>
        val dataShouldBeMoved = listUpToMove(cs)
        val recipientNode = findContainerNode(self, dataShouldBeMoved)
        val moving = recipientNode map moveChunk(dataShouldBeMoved, self).tupled

        moving.toList.sequence match {
          case Some(_) => cs.copy(dataholder = cs.dataholder -- dataShouldBeMoved.keys).some
          case None => cs.some
        }
    }
  } | cs

  def listUpToMove(cs: ChordState): Map[Seq[Byte], KVSData] = {
    cs.dataholder.filterKeys {
      (key: Seq[Byte]) =>
        logger.info(s"Combined SuccFingerList: ${(cs.succList.nodes.list ++ cs.fingerList.nodes.list).mkString("{", ", ", "}")}")
        nodeID(key.toArray).belongs_between(cs.selfID.get).and(cs.succList.nearestSuccessor(cs.selfID.get)) ||
          !nodeID(key.toArray).belongs_between(cs.selfID.get).and(NodeList(cs.succList.nodes.list ++ cs.fingerList.nodes.list).nearestNeighbor(nodeID(key.toArray), cs.selfID.get))
    }
  }

  def findContainerNode(self: idAddress, map: Map[Seq[Byte], KVSData]): Map[Seq[Byte], IdAddressMessage] = {
    map map {
      (f: (Seq[Byte], KVSData)) =>
        (f._1, Await.result(self.getTransmitter.findNode(nodeID(f._1.toArray)), 50 second))
    }
  }

  val moveChunk = (toMove: Map[Seq[Byte], KVSData], self: idAddress) => (key: Seq[Byte], idam: IdAddressMessage) => {
    idam.idaddress.get.getTransmitter.setChunk(key, toMove(key))
  }
}
