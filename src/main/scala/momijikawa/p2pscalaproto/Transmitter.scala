package momijikawa.p2pscalaproto

import akka.actor._
import scala.util.control.Exception._
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util.Timeout

/**
 * アクター間のメッセージパッシングを同期式のメソッドとしてラッピングします。
 * @param remote 送り宛
 */
class Transmitter(remote: ActorRef) {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val RemoteActor = remote
  implicit val timeout = Timeout(50 seconds)

  def whoAreYou: Option[idAddress] = allCatch opt {
    WhoAreYou.!?[IdAddressMessage](RemoteActor).idaddress.get
  }

  def checkLiving: Boolean = allCatch opt {
    implicit val timeout = Timeout(5 seconds)
    scala.concurrent.Await.result(RemoteActor.ask(Ping), 5 second)
    true
  } getOrElse false

  def ping(): Future[String] = (RemoteActor ask Ping).mapTo[PingACK] map {
    _.id_base64
  }

  def findNode(id_query: TnodeID): Future[IdAddressMessage] = {
    allCatch opt {
      val result = RemoteActor.ask(FindNode(id_query.getBase64)).mapTo[IdAddressMessage]
      result
    } getOrElse (Future.successful(IdAddressMessage(None)))
  }

  def findNodeShort(id_query: TnodeID)(implicit context: ActorContext): Option[idAddress] = allCatch opt {
    (RemoteActor.forward(FindNode(id_query.getBase64))).asInstanceOf[IdAddressMessage].idaddress.get
  }

  def amIPredecessor(selfId: idAddress) = RemoteActor ! AmIPredecessor(selfId)

  def yourPredecessor: Future[IdAddressMessage] = (RemoteActor ? YourPredecessor).mapTo[IdAddressMessage]

  def yourSuccessor: Future[IdAddressMessage] = (RemoteActor ? YourSuccessor).mapTo[IdAddressMessage]

  def setChunk(id: Seq[Byte], data: KVSData): Option[Seq[Byte]] = SetChunk(id, data).!?[Option[Seq[Byte]]](RemoteActor)

  def getChunk(id: Seq[Byte]): Option[KVSData] = GetChunk(id).!?[Option[KVSData]](RemoteActor)
}
