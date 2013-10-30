package momijikawa.p2pscalaproto

import akka.actor._
import akka.pattern.ask
import scala.reflect.ClassTag
import akka.util.Timeout
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class Message {
  // not sealed because of extension
  def !?[T: ClassTag](a: ActorRef): T = {
    implicit val timeout = Timeout(50 second)
    val f: Future[T] = (a ? this).mapTo[T]
    Await.result(f, 50 second)
  }
}

class nodeMessage extends Message

case object Ping extends nodeMessage

case object WhoAreYou extends nodeMessage

case class FindNode(queryID: String) extends nodeMessage

case object YourPredecessor extends nodeMessage

case object YourSuccessor extends nodeMessage

case class AmIPredecessor(id: idAddress) extends nodeMessage

case class GetChunk(id: Seq[Byte]) extends nodeMessage

case class SetChunk(id: Seq[Byte], kvp: KVSData) extends nodeMessage

case class IdAddressMessage(idaddress: Option[idAddress]) extends nodeMessage

case class PingACK(id_base64: String) extends nodeMessage

case class ChankReturn(id: Seq[Byte], Value: KVSData) extends nodeMessage


class stabilizeMessage extends Message

case object StartStabilize extends stabilizeMessage

case object StopStabilize extends stabilizeMessage

class chordMessage extends Message

case object Stabilize extends chordMessage

case class InitNode(id: nodeID) extends chordMessage

case class PutData(title: String, value: Stream[Byte]) extends chordMessage

case class GetData(key: Seq[Byte]) extends chordMessage

case class JoinNode(connectTo: idAddress) extends chordMessage

case object GetStatus extends chordMessage

case object Serialize extends chordMessage

case object ACK extends chordMessage
