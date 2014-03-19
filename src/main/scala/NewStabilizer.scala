package momijikawa.p2pscalaproto

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.agent.Agent
import scala.concurrent.stm._
import akka.actor.ActorContext
import WatchableObject._
import LoggerLikeObject._
import scalaz._
import Scalaz._
import scalaz.Ordering.GT

class NewStabilizer(watcher: Watchable, logger: LoggerLike) {
  val stabilize: ChordState => ChordState = (state: ChordState) => {

    require(state.selfID.isDefined)

    val self = state.selfID.get
    val succ = state.succList.nearestSuccessor(self)
    if (succ.asNodeID == self.asNodeID) {
      logger.debug("This node is alone. Doing nothing...")
      state
    } else {
      succ.getTransmitter.checkLiving match {
        case true =>
          predecessor_of(succ) match {
            case Some(x) =>
              if (x.belongs_between(self) and (succ)) {
                logger.info("Successor has predecessor which seems to be suitable as successor; changing...")
                notify_to(self)(x)
                state.copy(succList = NodeList(List(x)))
              } else {
                // predecessor of successor is this node.
                notify_to(self)(x)
                state |> increaseSuccessor >>> immigrateData
              }
            case None =>
              logger.info("Successor has no predecessor; notifying...")
              notify_to(self)(succ)
              state
          }
        case false =>
          logger.info("Successor seems to be dead; disconnecting...")
          state.succList.nodes.size ?|? 1 match {
            case GT => recoverSuccList(state) // SuccListに余裕があるとき
            case _ => joinPred(state)
          }
      }
    }
  }

  private def notify_to(self: idAddress)(node: idAddress) = {
    node.getTransmitter.amIPredecessor(self)
  }

  private def predecessor_of(node: idAddress): Option[idAddress] = {
    Await.result(node.getTransmitter.yourPredecessor, 20 seconds).idaddress
  }

  private def joinNetwork(state: ChordState, ida: idAddress): (ChordState, Option[idAddress]) = ChordState.joinNetworkS(ida).run(state)

  private val joinPred: (ChordState) => ChordState =
    (state: ChordState) => {
      // TODO: 簡潔にリファクタする
      logger.info("Joining predecessor as successor...")
      val joinedResult =
        for {
          pred <- state.pred
          joinedTrying <- Some(joinNetwork(state, pred))
        } yield joinedTrying

      joinedResult match {
        case Some((c, None)) => bunkruptNode(c)
        case Some((c, _)) => c // do nothing
        case None => state // do nothing
      }
    }

  private def bunkruptNode(state: ChordState): ChordState = {
    state.stabilizer.stop()
    state.copy(succList = NodeList(List[idAddress](state.selfID.get)), pred = None)
  }

  private def recoverSuccList(state: ChordState): ChordState = {
    logger.info("recovering successor list; disconnecting successor...")
    watcher.unwatch(state.succList.nearestSuccessor(state.selfID.get).actorref)
    val newState = state.copy(succList = state.succList.killNearest(state.selfID.get))
    joinNetwork(newState, newState.succList.nearestSuccessor(newState.selfID.get))._1 // TODO: predも使用できる
  }

  private val increaseSuccessor: ChordState => ChordState = (state: ChordState) => {
    logger.debug("going to add successor")

    val self = state.selfID.get
    val succ = state.succList.nearestSuccessor(self)
    def genList(succ: idAddress)(operation: idAddress => Option[idAddress])(length: Int): List[idAddress] = succ :: unfold(succ)(_ |> operation map (_.squared)).take(length).toList

    val newSuccList: List[idAddress] = genList(succ)(ida => Await.result[IdAddressMessage](ida.getTransmitter.yourSuccessor, 10 seconds).idaddress >>= { node => if (node.asNodeID == self.asNodeID) None else node.some })(4)

    newSuccList match {
      case lis if lis.isEmpty => {
        logger.warning("failed to increase successor")
        state
      }
      case lis =>
        logger.info("Successor list has been added")
        lis map { _.actorref } foreach { watcher.watch }
        state.copy(succList = NodeList(lis))
    }
  }

  private val immigrateData: ChordState => ChordState = (state: ChordState) => {

    def listUpToMove(cs: ChordState): Map[Seq[Byte], KVSData] = {
      cs.dataholder.filterKeys {
        (key: Seq[Byte]) =>
          //logger.info(s"Combined SuccFingerList: ${(cs.succList.nodes.list ++ cs.fingerList.nodes.list).mkString("{", ", ", "}")}")
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

    val dataShouldBeMoved = listUpToMove(state)
    val recipientNode = findContainerNode(state.selfID.get, dataShouldBeMoved)
    val moving = recipientNode map moveChunk(dataShouldBeMoved, state.selfID.get).tupled

    moving.toList.sequence match {
      case Some(_) => state.copy(dataholder = state.dataholder -- dataShouldBeMoved.keys)
      case None => state
    }

  }
}
