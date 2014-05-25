package momijikawa.p2pscalaproto

import akka.actor.{ ActorRef, Props, ActorSystem }
import com.typesafe.config.ConfigFactory
import akka.agent.Agent
import scala.collection.immutable.HashMap
import scala.concurrent.duration._
import messages._
import networking.UPnPOpener
/**
 * メインクラスです。Chordアルゴリズムを利用してDHTを実装します。
 */
class Chord(localNetworkCommunicator: LocalMessaging, remoteNetworkCommunicator: NetworkMessaging) {

  import scala.concurrent.Future
  import concurrent.ExecutionContext.Implicits.global
  import localNetworkCommunicator._
  import remoteNetworkCommunicator._

  val config = ConfigFactory.load()
  val customConf = config.getConfig("p2pakka").withFallback({
    config
  })
  val system = ActorSystem("ChordCore-DHT", ConfigFactory.load(customConf))
  val stateAgt: Agent[ChordState] = Agent(new ChordState(
    None,
    NodeList(List(idAddress(nodeID(Array.fill(20)(0.toByte)), receiver))),
    NodeList(List.fill(10)(idAddress(nodeID(Array.fill(20)(0.toByte)), receiver))),
    None,
    new HashMap[Seq[Byte], KVSData](), null
  ))(system.dispatcher)
  val receiver = system.actorOf(Props(classOf[MessageReceiver], stateAgt, remoteNetworkCommunicator), "Receiver")
  var uOpener: UPnPOpener = null

  /**
   * DHTを初期化します。
   * 所与の[[momijikawa.p2pscalaproto.nodeID]]でDHTを初期化します。自動的に安定化処理が開始します。
   * @param id 仮想空間上でのノードの位置情報
   * @return 返答があると[[ACK]]を返します。
   */
  def init(id: nodeID) = {
    import concurrent.duration._

    if (!customConf.atKey("automatic-portmap").isEmpty && customConf.getBoolean("automatic-portmap")) {
      val exp = customConf.getInt("akka.remote.netty.tcp.port")
      val inp = exp
      val uOpener = new UPnPOpener(exp, inp, "TCP", "P2PScalaProto", 1 hour)(system.log)

      system.log.debug(s"opening port [$exp]...")
      if (uOpener.open) {
        system.log.debug(s"UPnP port opened: $exp")
      } else {
        system.log.warning(s"UPnP port open failed: $exp")
      }
    }

    localSendReceiveAwait(receiver, InitNode(id), 3 seconds)
  }

  /**
   * DHTにデータを投入します。
   * DHTに所与のデータを投入し、取り出す際に必要となるキーを返します。データが投入されるノードは自動的に決定します。
   * @param title データのタイトル。通例SHA-1ハッシュです。
   * @param value データの内容。
   * @return データを取り出すためのキー。
   */
  def put(title: String, value: Stream[Byte]): Future[Option[Seq[Byte]]] = {
    system.log.debug("chord: put called")
    localSendReceive[PutDataResult](receiver, PutData(title, value)) flatMap {
      case PutDataSuccessful(key) ⇒ Future(Some(key))
      case PutDataFailed(reason) ⇒
        system.log.warning(reason)
        Future(None)
    }
  }

  /**
   * DHTからデータを取り出します。
   * 所与のキーを用いてDHTからデータを取り出します。データは[[scala.concurrent.Future]]として返ります。
   * @param key データを投入した際に得たキー。
   * @return データのオプション型が[[scala.concurrent.Future]]で返ります。データが不在の場合、エラーの場合はNoneが返ります。
   */
  def get(key: Seq[Byte]): Future[Option[Seq[Byte]]] = {
    system.log.debug("chord: get called")
    localSendReceive[GetDataResult](receiver, GetData(key)) flatMap {
      case GetDataSuccessful(_, value) ⇒ Future(Some(value))
      case GetDataFailed(reason) ⇒
        system.log.warning(reason)
        Future(None)
    }
  }

  /**
   * DHT空間に参加します。
   * 所与のノードを踏み台として、DHT上のノードとしてネットワークに参加します。接続先は自動的に決定します。
   * @param ida 接続の踏み台(bootstrap)に使うノード。このノードを経由して自動的にネットワーク上の位置が決定します。
   * @return 了承したら[[ACK]]が返ります。
   */
  def join(ida: NodeIdentifier) = localSendReceive[ACK.type](receiver, JoinNode(ida))

  /**
   * レファレンス文字列を用いてDHT空間に参加します。シュガーシンタックスです。
   * @param str レファレンス文字列。
   * @return 了承したら[[ACK]]が返ります。
   */
  def join(str: String): Future[ACK.type] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    idAddress.fromString(str)(system) match {
      case Some(ida) ⇒ join(ida)
      case None ⇒
        system.log.error("Invalid node reference.")
        Future.failed(new Exception("Invalid node reference."))
    }
  }

  /**
   * [[momijikawa.p2pscalaproto.Chord.join(String)]]で必要となるレファレンス文字列を返します。
   * @return レファレンス文字列。
   */
  def getReference = localSendReceiveAwait[Option[String]](receiver, Serialize, 3 seconds)

  /**
   * 現在のノードの情報を返します。
   * @return 生のデータ。
   */
  def getStatus: Option[ChordState] = localSendReceiveAwait[ChordState](receiver, GetStatus, 3 seconds)

  /**
   * ノードを停止させます。
   */
  def close() = {
    localSendReceiveAwait(receiver, Finalize, 1 minute)
    system.shutdown()
    system.awaitTermination()
    if (uOpener != null) {
      uOpener.close
    }
  }
}
