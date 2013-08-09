package momijikawa.p2pscalaproto

import akka.actor._
import akka.event.Logging
import scala.collection.immutable.HashMap
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.Future

import scalaz._
import Scalaz._

/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/07/08
 * Time: 1:45
 * To change this template use File | Settings | File Templates.
 */
class ChordCore extends Actor {
  type dataMap = HashMap[Seq[Byte], KVSData]
  type idListT = List[idAddress]

  var state = new ChordState(None, List(), List(), None, new HashMap[Seq[Byte], KVSData](), null)
  val log = Logging(context.system, this)
  //val stabilizer = system.actorOf(Props[Stabilizer], "Stabilizer")
  //val transmitter = system.actorOf(Props[Transmitter], "Transmitter")

  def receive = {
    case m: chordMessage => m match {
      case Stabilize => stabilize()
      case InitNode(id) => init(id); sender ! ACK
      case JoinNode(connectTo) => join(connectTo); sender ! ACK
      case GetData(key) => sender ! loadData(key)
      case PutData(title, value) => sender ! saveData(title, value)
      case x => log.error("Unknown chordMessage:" + x)
    }
    case m: nodeMessage => m match {
      case Ping => sender ! PingACK
      case WhoAreYou => sender ! IdAddressMessage(state.selfID)
      //case FindNode(id: String) => sender ! findNodeAct(new nodeID(id))
      case FindNode(id: String) => findNodeActS(new nodeID(id))
      case AmIPredecessor(address) => ChordState.checkPredecessor(address).run(state)._1
      case YourPredecessor => sender ! yourPredecessorAct
      case YourSuccessor => sender ! yourSuccessorAct
      case SetChunk(key, kvp) =>
        val saved = ChordState.dataPut(key, kvp).run(state)
        state = saved._1
        sender ! saved._2
      case GetChunk(key) =>
        if (!state.dataholder.isDefinedAt(key)) {
          log.info(s"No data for key: ${nodeID(key.toArray)} while Bank: ${state.dataholder.keys.map {
            key => nodeID(key.toArray)
          }.mkString("¥n")}")
        } else {
          log.debug(s"found data for ${nodeID(key.toArray)}")
        }
        sender ! state.dataholder.get(key) //sender.!?[Array[Byte]](GetChunk(key))
      case x => log.error("No such Message: " + x.toString)
    }
    case x => log.error("unknown message:" + x)
  }

  def init(id: nodeID) = synchronized {
    implicit val executionContext = context.system.dispatchers.defaultGlobalDispatcher
    log.info("initializing node")
    state = state.copy(selfID = idAddress(id.bytes, self).some, succList = List(idAddress(id.bytes, self)))
    state = state.copy(fingerList = List.fill(10)(idAddress(id.bytes, self)), stabilizer = context.system.actorOf(Props(new Stabilizer(self, Stabilize)), name = "Stabilizer"))
    log.info("initialization successful: " + state.toString)
  }

  override def preStart = {
    log.debug("A ChordCore has been newed")
  }

  override def postRestart(reason: Throwable) = {
    log.debug(s"Restart reason: ${reason.getLocalizedMessage}")
    preStart
  }

  def join(to: idAddress) = {
    state = ChordState.join(to, state.stabilizer).run(state)._1
  }

  def ping: PingACK = PingACK(state.selfID.get.getBase64)

  //private def findNodeAct(id: TnodeID): IdAddressMessage = IdAddressMessage(ChordState.findNodeCore(state)(id))
  private def findNodeActS(id: TnodeID)(implicit context: ActorContext) = ChordState.findNodeCoreS(state, id)

  private def yourSuccessorAct: IdAddressMessage = IdAddressMessage(ChordState.yourSuccessorCore(state))

  private def yourPredecessorAct: IdAddressMessage = IdAddressMessage(ChordState.yourPredecessorCore(state))

  private def sleepForRandom = {
    try {
      Thread.sleep((math.random * 100).asInstanceOf[Long])
    } catch {
      case e: InterruptedException => e.printStackTrace()
    }
  }


  private def stabilize() = {
    System.out.println("I am: " + state.selfID.get.getBase64)
    System.out.println("successor is: "
      + state.succList.last.getBase64)


    state = successorStabilizationFactory.autoGenerate(state).apply(state)._1
    state = fingerStabilizer.stabilize.run(state)._1
  }

  /**
   * Predecessorが生きているかどうかを返します。
   * @return { @see #checkSuccLiving}と同じです。
   */
  private def checkPredLiving(): Boolean = {
    state.pred match {
      case Some(v) => v.getClient(state.selfID.get).checkLiving
      case None => false
    }
  }

  /**
   * データをノードに保管します。
   * @param title データのタイトル。
   * @param value データの内容。
   * @return 取り出す際に必要なユニークなID。
   */
  def saveData(title: String, value: Stream[Byte]): Future[Option[Seq[Byte]]] = {
    import context.dispatcher
    log.debug("Saving data into DHT.")
    //log.debug("state: " + state.toString)
    import java.security.MessageDigest
    val digestFactory = MessageDigest.getInstance("SHA-1")

    //System.out.println(meta.toString());
    /*
     * for (int i = 0; i > value.length; i += 1024) { if (i + 1024 >
     * value.length) { splitedData[i] = new String(value).substring(i, i +
     * 1024); } else { splitedData[i] = new String(value).substring(i,
     * value.length - 1); } }
     */
    val selfTransmitter: Transmitter = state.selfID.get.getClient(state.selfID.get)
    log.debug("Transmitter prepared.")
    /*val saving: (Transmitter) => (BigInt) => (Stream[Byte]) => (Stream[Array[Byte]], BigInt) = (trans: Transmitter) => (count: BigInt) => (tail: Stream[Byte]) => {
      tail.size match {
        case 0 => (Stream.empty, count)
        case _ =>
          val separated = tail.splitAt(1024)
          val addr: idAddress = trans.findNode(nodeID(separated._1.toArray)).getOrElse(state.selfID.get)
          val cli = addr.getClient(state.selfID.get)
          val digestFactory = MessageDigest.getInstance("SHA-1")
          digestFactory.update(separated._1.toArray)
          val digest = digestFactory.digest()
          cli.setChunk(digest, KVSValue(separated._1.toStream))
          val saved = saving(trans)(count + 1)(separated._2)
          (digest #:: saved._1, saved._2)
          saving(trans)(count + 1)(separated._2) |> {
            (nextdig: Stream[Array[Byte]], c: BigInt) => (digest #:: nextdig, c)
          }.tupled
      }
    }*/


    val saveStream: (Transmitter) => (Stream[Byte]) => Future[Option[Stream[Seq[Byte]]]] =
      (trans: Transmitter) => (tail: Stream[Byte]) => Future {
        val savedDataKeys = tail.grouped(1024).map {
          (chunk: Stream[Byte]) =>
            val digest = digestFactory.digest(chunk.toArray).toSeq
            (Await.result(trans.findNode(nodeID(chunk.toArray)), 50 second).idaddress | state.selfID.get).getClient(state.selfID.get).setChunk(digest, KVSValue(chunk))
        }.toStream
        savedDataKeys.sequence[Option, Seq[Byte]]
      }


    /*for (byteData <- value.) {
      val addr: idAddress = nd.findNode(Base64.encode(meta.checksum_Chunk_SHA1(i))).getOrElse(self)
      val cliNode: P2PMessageClient = addr.getClient;
      cliNode.setChunk(meta.checksum_Chunk_SHA1(i),
        meta.chunks(i).getBytes());
    }*/
    val digestsOpt = saveStream(selfTransmitter)(value)
    log.debug("saving message has sent.")

    digestsOpt map {
      _ >>= {
        (digests: Stream[Seq[Byte]]) =>
          val Bigzero = BigInt(0)
          val size = value.foldLeft(Bigzero)((l: BigInt, R: Byte) => l + 1)
          val chunkCountWithRoundup: BigInt = size.mod(1024) match {
            case Bigzero => size / 1024
            case _ => (size / 1024) + 1
          }
          val meta: MetaData = MetaData(title, chunkCountWithRoundup, size, digests)
          log.debug("metadata has generated.")
          digestFactory.update(meta.toString.getBytes)
          val metaDigest = digestFactory.digest()
          val addr: idAddress = Await.result(selfTransmitter.findNode(nodeID(metaDigest)), 30 second).idaddress | state.selfID.get
          val cliNode = addr.getClient(state.selfID.get)
          log.debug("Saving metadata.")
          cliNode.setChunk(metaDigest.toSeq, meta)
          metaDigest.toSeq.some
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
      log.debug("Loading data from DHT.")
      //log.debug("state: " + state.toString)

      val nd = state.selfID.get.getClient(state.selfID.get)
      val addr: idAddress = Await.result(nd.findNode(nodeID(key.toArray)), 50 second).idaddress | state.selfID.get
      val byte_meta: Option[KVSData] = addr.getClient(state.selfID.get).getChunk(key)
      log.debug(s"Metadata has loaded: ${byte_meta}")

      val concat = (L: Seq[KVSValue], R: Seq[KVSValue]) => L ++ R
      val KVSValueEnsuring: (Option[KVSData]) => Option[KVSValue] =
        (data: Option[KVSData]) =>
          data match {
            case Some(v) if v.isInstanceOf[KVSValue] => Some(v.asInstanceOf[KVSValue]) // 暗黙変換されるか心配
            case Some(v) => log.error(s"Received is not Data: ${v}"); None
            case None => log.error(s"not found"); None
          }

      val key2chunk = (key: Seq[Byte]) => // Validationの利用？
        for {
          _ <- log.debug(s"finding chunk for: ${nodeID(key.toArray).getBase64}").point[Option]
          addr <- Tags.First(Await.result(nd.findNode(nodeID(key.toArray)), 50 second).idaddress) |+| Tags.First(state.selfID) // findNodeに失敗したらself
          _ <- log.debug(s"addr for key: ${addr}").point[Option]
          selfid <- state.selfID
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
