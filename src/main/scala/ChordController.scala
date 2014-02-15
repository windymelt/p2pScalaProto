package momijikawa.p2pscalaproto

import akka.event.LoggingAdapter
import akka.actor._
import akka.agent.Agent

class ChordController(stateAgt: Agent[ChordState], implicit val context: ActorContext, log: LoggingAdapter) {

  import scalaz._
  import Scalaz._
  import scala.collection.immutable.HashMap
  import scala.concurrent.Await
  import scala.concurrent.duration._
  import scala.concurrent.Future
  import scala.concurrent.ops._
  import scala.concurrent.stm._

  val watcher = new NodeWatcher(context)
  val stabilizerFactory = new StabilizerFactory(context)
  val fingerStabilizer = new FingerStabilizer(watcher)
  implicit val dispatcher = context.dispatcher

  /**
   * ノードを初期化します。
   * ノード状態の自己IDならびにSuccessor、fingerlist、stabilizerを設定し、安定化処理を開始させます。
   * @param id ノードのID
   */
  def init(id: nodeID, receiver: ActorRef) = synchronized {
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val timeout = akka.util.Timeout(5 seconds)

    log.info("initializing node")

    atomic {
      implicit txn =>
        stateAgt send (_.copy(selfID = idAddress(id.bytes, receiver).some, succList = NodeList(List(idAddress(id.bytes, receiver)))))
        stateAgt send (_.copy(fingerList = NodeList(List.fill(10)(idAddress(id.bytes, receiver))), stabilizer = stabilizerFactory.generate(receiver)))
    }

    log.info("state initialized")

    Await.result(stateAgt.future(), 10 seconds).stabilizer ! StartStabilize

    log.info("initialization successful: " + stateAgt().toString)
  }

  /**
   * DHTネットワークに参加します。
   * @param to ブートストラップとして用いるノード。
   */
  def join(to: idAddress) = {
    val newSucc = ChordState.joinNetwork(to, stateAgt)
    newSucc >>= {
      ida =>
        watcher.watch(ida.actorref)
        ida.getClient(stateAgt().selfID.get).amIPredecessor()
        true.some
    }
  }

  /**
   * Pingを返します。
   * @return Ping情報。
   */
  def ping: PingACK = PingACK(stateAgt().selfID.get.getBase64)

  /**
   * 所与のノードIDを管轄するノードを検索します。
   * @param id 検索するノードID
   */
  def findNode(id: TnodeID) = ChordState.findNode(stateAgt, id)

  /**
   * Successorを[[momijikawa.p2pscalaproto.IdAddressMessage]]でラップして返します。
   * @return Successor
   */
  def yourSuccessor: IdAddressMessage = IdAddressMessage(ChordState.yourSuccessor(stateAgt()))

  /**
   * Predecessorを[[momijikawa.p2pscalaproto.IdAddressMessage]]でラップして返します。
   * @return Predecessor
   */
  def yourPredecessor: IdAddressMessage = {
    log.debug("YourPredecessor")
    IdAddressMessage(ChordState.yourPredecessor(stateAgt()))
  }

  /**
   * 実際の安定化処理を行ないます。この動作は別スレッドで行なわれます。
   */
  def stabilize() = spawn {
    log.debug("Stabilizing stimulated")
    val strategy = new successorStabilizationFactory(context, context.system.log).autoGenerate(stateAgt())
    log.debug("Strategy done: " + strategy.toString())
    stateAgt send fingerStabilizer.stabilize
    log.debug("fingertable stabilized")
  }

  def unregistNode(a: ActorRef) = {
    log.debug(s"unregisting node $a")
    stateAgt send {
      _.dropNode(a)
    }
  }

  /**
   * ノードを停止する前の処理を行ないます。
   * 保持しているデータを最近傍のノードに転送します。
   */
  def finalizeNode(): ACK.type = {
    import util.control.Exception._

    val self = stateAgt().selfID.get
    val nearest = allCatch opt NodeList(stateAgt().succList.nodes.list.filterNot(x => x == self)).nearestSuccessor(stateAgt().selfID.get).actorref

    nearest match {
      case Some(nrst) =>
        log.info("stopping stabilizer...")
        stateAgt().stabilizer ! StopStabilize
        log.info("transferring data to other nodes...")
        stateAgt().dataholder.foreach {
          case (key: Seq[Byte], value: KVSData) => SetChunk(key, value).!?[Option[Seq[Byte]]](nrst)
        }
        log.info("transferring complete.")
        ACK
      case None =>
        stateAgt().stabilizer ! StopStabilize
        ACK
    }
  }

  /**
   * 他のノードからデータをまとめて受け取ります。
   * @param data 他のノードからのデータ群。
   */
  def immigrateData(data: HashMap[Seq[Byte], KVSData]) = {
    stateAgt send {
      st => st.copy(dataholder = st.dataholder ++: data)
    }
  }

  /**
   * データをノードに保管します。
   * @param title データのタイトル。
   * @param value データの内容。
   * @return 取り出す際に必要なユニークなID。
   */
  def saveData(title: String, value: Stream[Byte]): Future[Option[Seq[Byte]]] = {
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
      val KVSValueEnsuring: (Option[KVSData]) => Option[KVSValue] = {
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
