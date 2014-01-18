package momijikawa.p2pscalaproto

import akka.actor._
import akka.event.Logging

/**
 * ノードに定期的に安定化の命令を発します。
 * @param chord 命令を受けるべきノードの[[akka.actor.ActorRef]]
 * @param message 安定化のトリガとなるメッセージ。
 * @param executionContext 文脈
 */
class Stabilizer(chord: ActorRef, message: chordMessage, implicit val executionContext: akka.dispatch.MessageDispatcher) extends Actor {

  import scala.concurrent.duration._
  import akka.agent.Agent

  var scheduler: Cancellable = _
  val log = Logging(context.system, this)
  val isStarted = Agent[Boolean](false)(executionContext)

  /**
   * メッセージを受けると安定化タイマ開始もしくは停止のいずれかの処理を行います。
   */
  def receive = {
    case m: stabilizeMessage =>
      m match {
        case StartStabilize => start()
        case StopStabilize => stop()
      }
    case _ => unhandled("unknown message")
  }

  /**
   * 安定化のスケジューリングを行います。
   * 既にスケジューリングが行なわれているときは何もしません。
   * スケジュール後に開始フラグを立てます。
   */
  def start() = {
    //scheduler.cancel()
    isStarted() match {
      case false =>
        log.info("stabilizer started")
        scheduler = context.system.scheduler.schedule(10 seconds, 5 seconds, chord, message)
        isStarted send true
      case true => // do nothing
    }
  }

  /**
   * 安定化のスケジューリングを停止します。
   * そもそもスケジューリングがないときは何もしません。
   * スケジューリングを中止した後に停止フラグを立てます。
   */
  def stop() = {
    isStarted() match {
      case true =>
        log.info("going to stop stabilizer")
        scheduler.cancel()
        isStarted send false
      case false => // do nothing
    }
  }

  override def postStop() = {
    log.debug("stabilizer has been terminated")
  }
}
