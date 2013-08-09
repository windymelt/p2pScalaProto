package momijikawa.p2pscalaproto

import akka.actor.{Props, ActorSystem}

/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/08/01
 * Time: 21:30
 * To change this template use File | Settings | File Templates.
 */
class Chord {

  import scala.concurrent.Future

  val system = ActorSystem("ChordCore-DHT")
  val chord = system.actorOf(Props[ChordCore], "ChordCore")

  def init(id: nodeID) = {
    InitNode(id).!?[ACK.type](chord)
  }

  def put(title: String, value: Stream[Byte]): Future[Option[Seq[Byte]]] = {
    //chord.!?[Option[Array[Byte]]](PutData(title, value))
    PutData(title, value).!?[Future[Option[Seq[Byte]]]](chord)
  }

  def get(key: Seq[Byte]): Future[Option[Stream[Byte]]] = {
    //chord.!?[Option[Stream[Byte]]](GetData(key))
    GetData(key).!?[Future[Option[Stream[Byte]]]](chord)
  }

  def join(ida: idAddress) = {
    JoinNode(ida).!?[ACK.type](chord)
  }

  def close() = {
    system.shutdown()
  }
}
