package momijikawa.p2pscalaproto

import scala.concurrent.{ TimeoutException, Await, Future }
import scala.concurrent.duration.Duration
import momijikawa.p2pscalaproto.messages.Message

trait NetworkMessaging {
  def sendReceive[A <: Message](sendTo: NodeIdentifier, message: Message): Future[A]
  def sendReceiveAwait[A <: Message](sendTo: NodeIdentifier, message: Message, duration: Duration): Option[A] = {
    try {
      Some(Await.result[A](sendReceive(sendTo, message), duration))
    } catch {
      case e: TimeoutException ⇒ None
    }
  }
}
