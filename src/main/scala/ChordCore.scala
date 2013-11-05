package momijikawa.p2pscalaproto

import akka.actor._
import akka.event.Logging
import scala.collection.immutable.HashMap
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ops._
import akka.agent.Agent
import scala.concurrent.stm._

import scalaz._
import Scalaz._

// TODO: timer-control-actorを作るべきでは

/**
 * Chord DHTの中核部です。
 * 高速なメッセージパッシングを処理するため、[[akka.actor.Actor]]で構成されています。
 * 通常の利用では直接扱うことはありません。
 */
class ChordCore extends Actor {
  type dataMap = HashMap[Seq[Byte], KVSData]

  val stateAgt = Agent(new ChordState(
    None,
    NodeList(List(idAddress(Array.fill(20)(0.toByte), self))),
    NodeList(List.fill(10)(idAddress(Array.fill(20)(0.toByte), self))),
    None,
    new HashMap[Seq[Byte], KVSData](), null
  ))(context.system)

  val log = Logging(context.system, this)

  /**
   * 受け取ったメッセージを処理します。
   */
  def receive = {
    case m: chordMessage => m match {
      case Stabilize => stabilize()
      case InitNode(id) =>
        init(id); sender ! ACK
      case JoinNode(connectTo) =>
        join(connectTo); sender ! ACK
      case GetData(key) => sender ! loadData(key)
      case PutData(title, value) => sender ! saveData(title, value)
      case Serialize => sender ! stateAgt().selfID.map(_.toString) //state.selfID.map(_.toString)
      case GetStatus => sender ! stateAgt()
      case Finalize => finalizeNode()
      case x => receiveExtension(x, sender)
    }
    case m: nodeMessage => m match {
      case Ping => sender ! PingACK
      case WhoAreYou => sender ! IdAddressMessage(stateAgt().selfID)
      //case FindNode(id: String) => sender ! findNodeAct(new nodeID(id))
      case FindNode(id: String) =>
        log.debug("chordcore: findnode from" + sender.path.toStringWithAddress(sender.path.address)); findNodeActS(new nodeID(id))
      case AmIPredecessor(address) => ChordState.checkPredecessor(address, stateAgt)
      case YourPredecessor => sender ! yourPredecessorAct
      case YourSuccessor => sender ! yourSuccessorAct
      case Immigration(data) => immigrateData(data)
      case SetChunk(key, kvp) =>
        val saved: Option[Seq[Byte]] = ChordState.dataPut(key, kvp, stateAgt)
        //        state = saved._1
        sender ! saved
      case GetChunk(key) =>
        log.debug("DHT: getchunk received.")
        if (!stateAgt().dataholder.isDefinedAt(key)) {
          log.info(s"No data for key: ${nodeID(key.toArray)} while Bank: ${
            stateAgt().dataholder.keys.map {
              key => nodeID(key.toArray)
            }.mkString("¥n")
          }")
        } else {
          log.debug(s"found data for ${nodeID(key.toArray)}")
        }
        sender ! stateAgt().dataholder.get(key) // => Option[KVSData] //sender.!?[Array[Byte]](GetChunk(key))
      case x => receiveExtension(x, sender)
    }
    case x => receiveExtension(x, sender)
  }

  /**
   * 継承によりアプリケーションで拡張できるメッセージ処理部です。
   * @param x 送られてきたメッセージ。
   * @param sender 送信した[[akka.actor.Actor]]
   * @param context [[akka.actor.ActorContext]]
   */
  def receiveExtension(x: Any, sender: ActorRef)(implicit context: ActorContext) = x match {
    case m => log.error(s"unknown message: $m")
  }

  /**
   * ノードを初期化します。
   * ノード状態の自己IDならびにSuccessor、fingerlist、stabilizerを設定し、安定化処理を開始させます。
   * @param id ノードのID
   */
  def init(id: nodeID) = synchronized {
    implicit val executionContext = context.system.dispatchers.defaultGlobalDispatcher
    implicit val timeout = akka.util.Timeout(5 seconds)

    log.info("initializing node")

    atomic {
      implicit txn =>
        stateAgt send (_.copy(selfID = idAddress(id.bytes, self).some, succList = NodeList(List(idAddress(id.bytes, self)))))
        stateAgt send (_.copy(fingerList = NodeList(List.fill(10)(idAddress(id.bytes, self))), stabilizer = context.actorOf(Props(new Stabilizer(self, Stabilize)), name = "Stabilizer")))
    }

    log.info("state initialized")

    stateAgt.await.stabilizer ! StartStabilize

    log.info("initialization successful: " + stateAgt().toString)
  }

  override def preStart() = {
    log.debug("A ChordCore has been newed")
  }

  override def postRestart(reason: Throwable) = {
    log.debug(s"Restart reason: ${reason.getLocalizedMessage}")
    preStart()
  }

  override def postStop() = {
    stateAgt().stabilizer ! StopStabilize
    // all beacon should be stopped.
    log.debug("Chordcore has been terminated")
  }

  /**
   * DHTネットワークに参加します。
   * @param to ブートストラップとして用いるノード。
   */
  def join(to: idAddress) = {
    ChordState.joinA(to, stateAgt)
  }

  /**
   * Pingを返します。
   * @return Ping情報。
   */
  def ping: PingACK = PingACK(stateAgt().selfID.get.getBase64)

  /**
   * 所与のノードIDを管轄するノードを検索します。
   * @param id 検索するノードID
   * @param context 返信に必要な[[akka.actor.ActorContext]]
   */
  private def findNodeActS(id: TnodeID)(implicit context: ActorContext) = ChordState.findNodeCoreS(stateAgt, id)

  /**
   * Successorを[[momijikawa.p2pscalaproto.IdAddressMessage]]でラップして返します。
   * @return Successor
   */
  private def yourSuccessorAct: IdAddressMessage = IdAddressMessage(ChordState.yourSuccessorCore(stateAgt()))

  /**
   * Predecessorを[[momijikawa.p2pscalaproto.IdAddressMessage]]でラップして返します。
   * @return Predecessor
   */
  private def yourPredecessorAct: IdAddressMessage = {
    log.debug("YourPredecessorAct"); IdAddressMessage(ChordState.yourPredecessorCore(stateAgt()))
  }

  /**
   * 実際の安定化処理を行ないます。この動作は別スレッドで行なわれます。
   */
  private def stabilize() = spawn {

    log.debug("Stabilizing stimulated")

    val strategy = successorStabilizationFactory.autoGenerate(stateAgt).doStrategy()

    log.debug("Strategy done: " + strategy.toString())

    stateAgt send (fingerStabilizer.stabilize.run(_)._1)

    log.debug("fingertable stabilized")
  }

  /**
   * ノードを停止する前の処理を行ないます。
   * 保持しているデータを最近傍のノードに転送します。
   */
  def finalizeNode() = {
    val self = stateAgt().selfID.get
    val nearest = NodeList(stateAgt().succList.nodes.list.filter(x => x != self)).nearestSuccessor(stateAgt().selfID.get).actorref
    stateAgt().dataholder.foreach{
      case (key: Seq[Byte], value: KVSData) => nearest ! SetChunk(key, value)
    }
  }

  /**
   * 他のノードからデータをまとめて受け取ります。
   * @param data 他のノードからのデータ群。
   */
  def immigrateData(data: HashMap[Seq[Byte], KVSData]) = {
    stateAgt send {st => st.copy(dataholder = st.dataholder ++: data)}
  }

  /**
   * データをノードに保管します。
   * @param title データのタイトル。
   * @param value データの内容。
   * @return 取り出す際に必要なユニークなID。
   */
  def saveData(title: String, value: Stream[Byte]): Future[Option[Seq[Byte]]] = {
    import context.dispatcher
    import java.security.MessageDigest

    type ChunkID = Seq[Byte]
    log.debug("Saving data into DHT.")

    val digestFactory = MessageDigest.getInstance("SHA-1")

    /* ----- Begin of declaring helper functions -----*/
    implicit val selfTransmitter: Transmitter = stateAgt().selfID.get.getClient(stateAgt().selfID.get)
    log.debug("Transmitter prepared.")

    def findNodeForChunk(id: ChunkID)(implicit selfTransmitter: Transmitter) =
      Await.result(selfTransmitter.findNode(nodeID(id.toArray)), 50 second).idaddress | stateAgt().selfID.get

    def saveStream(data: Stream[Byte])(implicit selfTransmitter: Transmitter): Future[Option[Stream[ChunkID]]] = Future {
      val savedDataKeys: Stream[Option[ChunkID]] = data.grouped(1024).map {
        (chunk: Stream[Byte]) =>
          val digestOfChunk = digestFactory.digest(chunk.toArray).toSeq
          findNodeForChunk(digestOfChunk).getClient(stateAgt().selfID.get)
            .setChunk(digestOfChunk, KVSValue(chunk))
      }.toStream
      savedDataKeys.sequence[Option, ChunkID]
    }
    /* ----- End of declaring functions ----- */
    /* ----- Beginning of renewing functions ----- */
    val digestsOpt: Future[Option[Stream[ChunkID]]] = saveStream(value)
    log.debug("saving message has sent.")

    def generateMetaData(digests: Stream[ChunkID]): MetaData = {
      val BigZERO = BigInt(0)
      val sizeOfValue = value.foldLeft(BigZERO)((sum: BigInt, a: Byte) => sum + 1) // Int would overflow so BigInt
      val chunkCount = sizeOfValue.mod(1024) match {
          case BigZERO => sizeOfValue / 1024
          case otherwise => (sizeOfValue / 1024) + 1
        }
      MetaData(title, chunkCount, sizeOfValue, digests)
    }

    def saveMetadata(metaData: MetaData): Option[ChunkID] = {
      val digestOfMetadata = digestFactory.digest(metaData.toString.getBytes)
      val saveTarget = findNodeForChunk(digestOfMetadata).getClient(stateAgt().selfID.get)
      saveTarget.setChunk(digestOfMetadata.toSeq, metaData)
      digestOfMetadata.toSeq.some
    }
    /* ----- end of renewing functions ----- */

    digestsOpt map {
      _ >>= {
        (digests: Stream[ChunkID]) =>
          val meta = generateMetaData(digests)
          saveMetadata(meta)
      }
    }

  }

  /**
   * データをネットワークから検索して返します。
   * @param key データのキー。
   * @return データが存在する場合はSomeが、存在しない場合はNoneを返します。
   */
  def loadData(key: Seq[Byte]): Future[Option[Stream[Byte]]] = {
    import context.dispatcher
    Future {
      log.debug("loaddata: Loading data from DHT.")
      //log.debug("state: " + state.toString)

      val nd = stateAgt().selfID.get.getClient(stateAgt().selfID.get)
      val findnodeF = nd.findNode(nodeID(key.toArray))
      log.debug("loaddata: findnode future fetched")
      val addr: idAddress = Await.result(findnodeF, 50 second).idaddress | stateAgt().selfID.get
      log.debug("loaddata: findnode done.")
      val byte_meta: Option[KVSData] = addr.getClient(stateAgt().selfID.get).getChunk(key)
      log.debug(s"loaddata: Metadata has loaded: $byte_meta")

      val concat = (L: Seq[KVSValue], R: Seq[KVSValue]) => L ++ R
      val KVSValueEnsuring: (Option[KVSData]) => Option[KVSValue] =
        {
          case Some(v) if v.isInstanceOf[KVSValue] => Some(v.asInstanceOf[KVSValue]) // 暗黙変換されるか心配
          case Some(v) =>
            log.error(s"Received is not Data: $v"); None
          case None => log.error(s"not found"); None
        }

      val key2chunk = (key: Seq[Byte]) => // Validationの利用？
        for {
          _ <- log.debug(s"finding chunk for: ${nodeID(key.toArray).getBase64}").point[Option]
          addr <- Tags.First(Await.result(nd.findNode(nodeID(key.toArray)), 50 second).idaddress) |+| Tags.First(stateAgt().selfID) // findNodeに失敗したらself
          _ <- log.debug(s"addr for key: $addr").point[Option]
          selfid <- stateAgt().selfID
          transmitter <- addr.getClient(selfid).some
          chunk <- transmitter.getChunk(key)
        } yield chunk

      byte_meta match {
        case None => None
        case Some(MetaData(title, count, size, digests)) =>
          log.debug("Digests: " + digests.map(dg => nodeID(dg.toArray).getBase64).mkString("¥n"))
          val ensured = digests.map(key2chunk).map(KVSValueEnsuring)
          ensured.sequence match {
            case None => None
            case Some(valSeq: Stream[KVSValue]) => Some(valSeq.map {
              (v: KVSValue) => v.value
            }.flatten.toStream)
          }
        case Some(_) => None
      }
    }

  }
}
