package momijikawa.p2pscalaproto

import scala.concurrent.{ TimeoutException, Await, Future }
import scala.concurrent.duration.Duration
import akka.actor.ActorRef
import momijikawa.p2pscalaproto.messages.Message

trait LocalMessaging {
  def localSendReceive[A](sendTo: ActorRef, message: Message): Future[A]
  def localSendReceiveAwait[A](sendTo: ActorRef, message: Message, duration: Duration): Option[A] = {
    try {
      Some(Await.result[A](localSendReceive(sendTo, message), duration))
    } catch {
      case e: TimeoutException ⇒ None
    }
  }
}
