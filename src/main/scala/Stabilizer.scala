package momijikawa.p2pscalaproto

import akka.actor._

/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/07/08
 * Time: 2:05
 * To change this template use File | Settings | File Templates.
 */
class Stabilizer(chord: ActorRef, message: chordMessage)(implicit override val context: ActorContext, executionContext: akka.dispatch.MessageDispatcher) extends Actor {

  import scala.concurrent.duration._

  var scheduler: Cancellable = _

  def receive = {
    case m: stabilizeMessage =>
      m match {
        case StartStabilize => start
        case StopStabilize => stop
      }
    case _ => unhandled("unknown message")
  }

  def start = {
    scheduler.cancel()
    scheduler = context.system.scheduler.schedule(0 milliseconds, 10 seconds, chord, message)
  }

  def stop = {
    context.system.log.debug("going to stop stabilizer")
    scheduler.cancel()
  }
}
