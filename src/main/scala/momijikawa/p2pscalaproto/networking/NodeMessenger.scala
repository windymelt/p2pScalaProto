package momijikawa.p2pscalaproto.networking

import akka.actor._
import scala.util.control.Exception._
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util.Timeout
import momijikawa.p2pscalaproto._
import momijikawa.p2pscalaproto.messages._
import momijikawa.p2pscalaproto.messages.FindNode
import momijikawa.p2pscalaproto.messages.NodeIdentifierMessage
import momijikawa.p2pscalaproto.messages.AmIPredecessor
import momijikawa.p2pscalaproto.messages.SetChunk

/**
 * アクター間のメッセージパッシングを同期式のメソッドとしてラッピングします。
 * @param remote 送り宛
 */
class NodeMessenger(remote: ActorRef) {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val RemoteActor = remote
  implicit val timeout = Timeout(50 seconds)

  def whoAreYou: Option[NodeIdentifier] = allCatch opt {
    WhoAreYou.!?[NodeIdentifierMessage](RemoteActor).identifier.get
  }

  def checkLiving: Boolean = allCatch opt {
    implicit val timeout = Timeout(5 seconds)
    scala.concurrent.Await.result(RemoteActor.ask(Ping), 5 second)
    true
  } getOrElse false

  def ping(): Future[String] = (RemoteActor ask Ping).mapTo[PingACK] map {
    _.id_base64
  }

  def findNode(id_query: TnodeID): Future[NodeIdentifierMessage] = {
    allCatch opt {
      val result = RemoteActor.ask(FindNode(id_query.getBase64)).mapTo[NodeIdentifierMessage]
      result
    } getOrElse (Future.successful(NodeIdentifierMessage(None)))
  }

  def findNodeShort(id_query: TnodeID)(implicit context: ActorContext): Option[NodeIdentifier] = allCatch opt {
    (RemoteActor.forward(FindNode(id_query.getBase64))).asInstanceOf[NodeIdentifierMessage].identifier.get
  }

  def amIPredecessor(selfId: NodeIdentifier) = RemoteActor ! AmIPredecessor(selfId)

  def yourPredecessor: Future[NodeIdentifierMessage] = (RemoteActor ? YourPredecessor).mapTo[NodeIdentifierMessage]

  def yourSuccessor: Future[NodeIdentifierMessage] = (RemoteActor ? YourSuccessor).mapTo[NodeIdentifierMessage]

  def setChunk(id: Seq[Byte], data: KVSData): Option[Seq[Byte]] = SetChunk(id, data).!?[Option[Seq[Byte]]](RemoteActor)

  def getChunk(id: Seq[Byte]): Option[KVSData] = GetChunk(id).!?[Option[KVSData]](RemoteActor)
}
