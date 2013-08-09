package momijikawa.p2pscalaproto

import akka.actor._
import scala.util.control.Exception._
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.util.Timeout

/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/07/08
 * Time: 2:46
 * To change this template use File | Settings | File Templates.
 */
class Transmitter(remote: ActorRef, selfId: idAddress) {

  implicit val RemoteActor = remote
  implicit val timeout = Timeout(50 seconds)

  def whoAreYou: Option[idAddress] = allCatch opt {
    WhoAreYou.!?[IdAddressMessage](RemoteActor).idaddress.get
  }

  def checkLiving: Boolean = allCatch opt {
    Ping.!?[PingACK](RemoteActor);
    true
  } getOrElse false

  def ping(): Option[String] = allCatch opt {
    Ping.!?[PingACK](RemoteActor).asInstanceOf[String]
  }

  def findNode(id_query: TnodeID): Future[IdAddressMessage] =
    allCatch opt {
      RemoteActor.ask(FindNode(id_query.getBase64)).mapTo[IdAddressMessage]
    } getOrElse (Future.successful(IdAddressMessage(None)))

  //FindNode(id_query.getBase64)[IdAddressMessage](RemoteActor).idaddress.get

  def findNodeShort(id_query: TnodeID)(implicit context: ActorContext): Option[idAddress] = allCatch opt {
    (RemoteActor.forward(FindNode(id_query.getBase64))).asInstanceOf[IdAddressMessage].idaddress.get
  }

  def amIPredecessor() = RemoteActor ! AmIPredecessor(selfId)

  def yourPredecessor: Option[idAddress] = YourPredecessor.!?[IdAddressMessage](RemoteActor).idaddress //(RemoteActor !? YourPredecessor).asInstanceOf[Option[idAddress]]

  def yourSuccessor: Option[idAddress] = YourSuccessor.!?[IdAddressMessage](RemoteActor).idaddress //(RemoteActor !? YourSuccessor).asInstanceOf[Option[idAddress]]

  def setChunk(id: Seq[Byte], data: KVSData): Option[Seq[Byte]] = {
    //RemoteActor.!?(SetChunk(id, data)).asInstanceOf[Option[Array[Byte]]]
    Some(SetChunk(id, data).!?[Seq[Byte]](RemoteActor))
  }

  def getChunk(id: Seq[Byte]): Option[KVSData] = {
    //RemoteActor.!?[Option[KVSData]](GetChunk(id))
    GetChunk(id).!?[Option[KVSData]](RemoteActor)
  }


}