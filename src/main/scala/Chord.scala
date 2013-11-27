package momijikawa.p2pscalaproto

import akka.actor.{ActorRef, Props, ActorSystem}
import com.sun.org.apache.xml.internal.security.utils.Base64
import com.typesafe.config.ConfigFactory

class Chord {

  import scala.concurrent.Future

  val config = ConfigFactory.load()
  val customConf = config.getConfig("p2pakka").withFallback({
    println("fallbacking");
    config
  })
  val system = ActorSystem("ChordCore-DHT", ConfigFactory.load(customConf))
  val chord = system.actorOf(Props(classOf[ChordCore]), "ChordCore")
  var uOpener: UPnPOpener = null

  /**
   * DHTを初期化します。
   * 所与の[[momijikawa.p2pscalaproto.nodeID]]でDHTを初期化します。自動的に安定化処理が開始します。
   * @param id 仮想空間上でのノードの位置情報
   * @return 返答があると[[momijikawa.p2pscalaproto.ACK]]を返します。
   */
  def init(id: nodeID) = {
    import concurrent.duration._

    if (!customConf.atKey("automatic-portmap").isEmpty && customConf.getBoolean("automatic-portmap")) {
      val exp = customConf.getInt("akka.remote.netty.tcp.port")
      val inp = exp
      uOpener = new UPnPOpener(exp, inp, "TCP", "P2PScalaProto", 1 hour)(system.log)

      system.log.debug(s"opening port [$exp]...")
      if (uOpener.open) {
        system.log.debug(s"UPnP port opened: $exp")
      } else {
        system.log.warning(s"UPnP port open failed: $exp")
      }
    }

    InitNode(id).!?[ACK.type](chord)
  }

  /**
   * DHTにデータを投入します。
   * DHTに所与のデータを投入し、取り出す際に必要となるキーを返します。データが投入されるノードは自動的に決定します。
   * @param title データのタイトル。通例SHA-1ハッシュです。
   * @param value データの内容。
   * @return データを取り出すためのキー。
   */
  def put(title: String, value: Stream[Byte]): Future[Option[Seq[Byte]]] = {
    //chord.!?[Option[Array[Byte]]](PutData(title, value))
    system.log.debug("chord: put called")
    val result: Future[Option[Seq[Byte]]] = PutData(title, value).!?[Future[Option[Seq[Byte]]]](chord)
    system.log.debug("chord: put done")
    result
  }

  /**
   * DHTからデータを取り出します。
   * 所与のキーを用いてDHTからデータを取り出します。データは[[scala.concurrent.Future]]として返ります。
   * @param key データを投入した際に得たキー。
   * @return データのオプション型が[[scala.concurrent.Future]]で返ります。データが不在の場合、エラーの場合はNoneが返ります。
   */
  def get(key: Seq[Byte]): Future[Option[Stream[Byte]]] = {
    //chord.!?[Option[Stream[Byte]]](GetData(key))
    system.log.debug("chord: get called")
    val result = GetData(key).!?[Future[Option[Stream[Byte]]]](chord)
    system.log.debug("chord: get done")
    result
  }

  /**
   * DHT空間に参加します。
   * 所与のノードを踏み台として、DHT上のノードとしてネットワークに参加します。接続先は自動的に決定します。
   * @param ida 接続の踏み台(bootstrap)に使うノード。このノードを経由して自動的にネットワーク上の位置が決定します。
   * @return 了承したら[[momijikawa.p2pscalaproto.ACK]]が返ります。
   */
  def join(ida: idAddress) = JoinNode(ida).!?[ACK.type](chord)

  /**
   * レファレンス文字列を用いてDHT空間に参加します。シュガーシンタックスです。
   * @param str レファレンス文字列。
   * @return 了承したら[[momijikawa.p2pscalaproto.ACK]]が返ります。
   */
  def join(str: String): Future[ACK.type] = {
    import scala.concurrent.duration._
    import scala.concurrent.ExecutionContext.Implicits.global

    val spr = str.split("\n")
    val actorF = system.actorSelection(spr(1)).resolveOne(50 seconds)
    actorF flatMap {
      (a: ActorRef) => Future(join(idAddress(Base64.decode(spr(0)), a)))
    }
  }

  /**
   * [[momijikawa.p2pscalaproto.Chord.join( S t r i n g )]]で必要となるレファレンス文字列を返します。
   * @return レファレンス文字列。
   */
  def getReference = Serialize.!?[Option[String]](chord)

  /**
   * 現在のノードの情報を返します。
   * @return 生のデータ。
   */
  def getStatus: ChordState = GetStatus.!?[ChordState](chord)

  /**
   * ノードを停止させます。
   */
  def close() = {
    Finalize.!?(chord)
    system.shutdown()
    system.awaitTermination()
    if (uOpener != null) {
      uOpener.close
    }
  }
}
