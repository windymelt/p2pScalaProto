package momijikawa.p2pscalaproto

import akka.actor._
import akka.event.Logging
import scala.collection.immutable.HashMap
import akka.agent.Agent
import scalaz._
import Scalaz._

// TODO: timer-control-actorを作るべきでは

/**
 * Chord DHTの中核部です。
 * 高速なメッセージパッシングを処理するため、[[akka.actor.Actor]]で構成されています。
 * 通常の利用では直接扱うことはありません。
 */
class MessageReceiver(stateAgt: Agent[ChordState]) extends Actor {

  type dataMap = HashMap[Seq[Byte], KVSData]

  //(context.system.dispatcher)

  val log = Logging(context.system, this)
  val handler = new ChordController(stateAgt, context, log)

  /**
   * 受け取ったメッセージを処理します。
   */
  def receive = {
    case m: chordMessage => m match {
      case Stabilize => handler.stabilize()
      case InitNode(id) =>
        handler.init(id, self); sender ! ACK
      case JoinNode(bootstrapNode) =>
        handler.join(bootstrapNode); sender ! ACK
      case GetData(key) => sender ! handler.loadData(key)
      case PutData(title, value) => sender ! handler.saveData(title, value)
      case Serialize => sender ! stateAgt().selfID.map(_.toString) //state.selfID.map(_.toString)
      case GetStatus => sender ! stateAgt()
      case Finalize => sender ! handler.finalizeNode()
      case x => receiveExtension(x, sender)
    }
    case m: nodeMessage => m match {
      case Ping => sender ! PingACK
      case WhoAreYou => sender ! IdAddressMessage(stateAgt().selfID)
      //case FindNode(id: String) => sender ! findNodeAct(new nodeID(id))
      case FindNode(id: String) =>
        log.debug("chordcore: findnode from" + sender.path.toStringWithAddress(sender.path.address))
        handler.findNode(new nodeID(id))
      case AmIPredecessor(address) => ChordState.checkPredecessor(address, stateAgt)
      case YourPredecessor => sender ! handler.yourPredecessor
      case YourSuccessor => sender ! handler.yourSuccessor
      case Immigration(data) => handler.immigrateData(data)
      case SetChunk(key, kvp) =>
        val saved: Option[Seq[Byte]] = ChordState.putDataToNode(key, kvp, stateAgt)
        //        state = saved._1
        sender ! saved
      case GetChunk(key) =>
        log.debug("DHT: getchunk received.")
        if (!stateAgt().dataholder.isDefinedAt(key)) {
          log.info(s"No data for key: ${nodeID(key.toArray)} while Bank: ${
            stateAgt().dataholder.keys.map {
              key => nodeID(key.toArray)
            }.mkString("¥n")
          }")
        } else {
          log.debug(s"found data for ${nodeID(key.toArray)}")
        }
        sender ! stateAgt().dataholder.get(key) // => Option[KVSData] //sender.!?[Array[Byte]](GetChunk(key))
      case x => receiveExtension(x, sender)
    }
    case Terminated(a: ActorRef) => handler.unregistNode(a)
    case x => receiveExtension(x, sender)
  }

  /**
   * 継承によりアプリケーションで拡張できるメッセージ処理部です。
   * @param x 送られてきたメッセージ。
   * @param sender 送信した[[akka.actor.Actor]]
   * @param context [[akka.actor.ActorContext]]
   */
  def receiveExtension(x: Any, sender: ActorRef)(implicit context: ActorContext) = x match {
    case m => log.error(s"unknown message: $m")
  }

  override def preStart() = {
    log.debug("MessageReceiver has been newed")
  }

  override def postRestart(reason: Throwable) = {
    log.debug(s"Restart reason: ${reason.getLocalizedMessage}")
    preStart()
  }

  override def postStop() = {
    // all beacon should be stopped.
    log.debug("MessageReceiver has been terminated")
  }
}
