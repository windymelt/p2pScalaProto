package momijikawa.p2pscalaproto

import akka.event.LoggingAdapter
import akka.actor._
import akka.agent.Agent
import momijikawa.p2pscalaproto.networking.{ NodeFinderInjector, NodeMessenger, NodeWatcher }
import scala.util.control.Exception._
import scala.Some
import momijikawa.p2pscalaproto.nodeID
import momijikawa.p2pscalaproto.KVSValue
import momijikawa.p2pscalaproto.MetaData
import momijikawa.p2pscalaproto.stabilizer.{ NewStabilizer, StabilizerFactory }
import momijikawa.p2pscalaproto.messages.{ ACK, PingACK, NodeIdentifierMessage, SetChunk }

class ChordLogic(stateAgt: Agent[ChordState], implicit val context: ActorContext, log: LoggingAdapter, networkCommunicator: NetworkMessaging) {
  import networkCommunicator._
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
  val fingerStabilizer = new FingerStabilizer(watcher, stateAgt)
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
      implicit txn ⇒
        stateAgt send (_.copy(selfID = idAddress(nodeID(id.bytes), receiver).some, succList = NodeList(List(idAddress(nodeID(id.bytes), receiver)))))
        stateAgt send (_.copy(fingerList = NodeList(List.fill(160)(idAddress(nodeID(id.bytes), receiver))), stabilizer = stabilizerFactory.generate(receiver)))
    }
    log.info("state initialized")
    Await.result(stateAgt.future(), 10 seconds).stabilizer.start()
    log.info("initialization successful: " + stateAgt().toString)
  }

  /**
   * DHTネットワークに参加します。
   * @param bootstrapNode ブートストラップとして用いるノード。
   */
  def join(bootstrapNode: NodeIdentifier): Option[Boolean] = {
    log.info(s"Joining network via bootstrap node ${bootstrapNode.getBase64} ...")
    val newSucc = ChordState.joinNetwork(bootstrapNode, stateAgt)
    stateAgt().selfID >>= {
      self ⇒
        newSucc >>= {
          ida ⇒
            log.info(s"Joined network: new Successor is ${ida.getBase64} ; Setting predecessor...")
            //watcher.watch(ida.uri)
            stateAgt send { _.copy(pred = ida.some) }
            ida.getTransmitter.amIPredecessor(self)
            true.some
        }
    }
  }

  /**
   * 所与のノードIDを管轄するノードを検索します。
   * @param id 検索するノードID
   */
  def findNode(id: TnodeID) = new NodeFinderInjector(id, stateAgt).judge()

  /**
   * 実際の安定化処理を行ないます。この動作は別スレッドで行なわれます。
   */
  def stabilize() = spawn {
    log.debug("Stabilizing stimulated")
    Await.result(stateAgt alter new NewStabilizer(context, context.system.log).stabilize, 5 seconds)
    //log.debug("Strategy done: " + strategy.toString())
    fingerStabilizer.stabilize() // スタビライザが書き換える
    log.debug("fingertable stabilized")
  }

  /**
   * 所与のアクターをSuccessorTable/FingerTableから除名します。
   * @param a 除名の対象となるアクター。
   */
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
    val nearest = allCatch opt NodeList(stateAgt().succList.nodes.list.filterNot(x ⇒ x == self)).nearestSuccessor(stateAgt().selfID.get)

    nearest match {
      case Some(nrst) ⇒
        log.info("stopping stabilizer...")
        stateAgt().stabilizer.stop()
        log.info("transferring data to other nodes...")
        stateAgt().dataholder.foreach {
          case (key: Seq[Byte], value: KVSData) ⇒ sendReceiveAwait(nrst, SetChunk(key, value), 10 seconds) //SetChunk(key, value).!?[Option[Seq[Byte]]](nrst)
        }
        log.info("transferring complete.")
        ACK
      case None ⇒
        stateAgt().stabilizer.stop()
        ACK
    }
  }

  /**
   * 他のノードからデータをまとめて受け取ります。
   * @param data 他のノードからのデータ群。
   */
  def immigrateData(data: HashMap[Seq[Byte], KVSData]) = {
    stateAgt send {
      st ⇒ st.copy(dataholder = st.dataholder ++: data)
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
    implicit val selfTransmitter: NodeMessenger = stateAgt().selfID.get.getTransmitter
    log.debug("Transmitter prepared.")

    def findNodeForChunk(id: ChunkID)(implicit selfTransmitter: NodeMessenger) =
      Await.result(selfTransmitter.findNode(nodeID(id.toArray)), 50 second).identifier | stateAgt().selfID.get

    def saveStream(data: Stream[Byte])(implicit selfTransmitter: NodeMessenger): Future[Option[Stream[ChunkID]]] = Future {
      val savedDataKeys: Stream[Option[ChunkID]] = data.grouped(1024).map {
        (chunk: Stream[Byte]) ⇒
          val digestOfChunk = digestFactory.digest(chunk.toArray).toSeq
          findNodeForChunk(digestOfChunk).getTransmitter
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
      val sizeOfValue = value.foldLeft(BigZERO)((sum: BigInt, a: Byte) ⇒ sum + 1) // Int would overflow so BigInt
      val chunkCount = sizeOfValue.mod(1024) match {
        case BigZERO   ⇒ sizeOfValue / 1024
        case otherwise ⇒ (sizeOfValue / 1024) + 1
      }
      MetaData(title, chunkCount, sizeOfValue, digests)
    }

    def saveMetadata(metaData: MetaData): Option[ChunkID] = {
      val digestOfMetadata = digestFactory.digest(metaData.toString.getBytes)
      val saveTarget = findNodeForChunk(digestOfMetadata).getTransmitter
      saveTarget.setChunk(digestOfMetadata.toSeq, metaData)
      digestOfMetadata.toSeq.some
    }
    /* ----- end of renewing functions ----- */

    digestsOpt map {
      _ >>= {
        (digests: Stream[ChunkID]) ⇒
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

      val nd = stateAgt().selfID.get.getTransmitter
      val findnodeF = nd.findNode(nodeID(key.toArray))
      log.debug("loaddata: findnode future fetched")
      val addr: NodeIdentifier = Await.result(findnodeF, 50 second).identifier | stateAgt().selfID.get
      log.debug("loaddata: findnode done.")
      val byte_meta: Option[KVSData] = addr.getTransmitter.getChunk(key)
      log.debug(s"loaddata: Metadata has loaded: $byte_meta")

      val concat = (L: Seq[KVSValue], R: Seq[KVSValue]) ⇒ L ++ R
      val KVSValueEnsuring: (Option[KVSData]) ⇒ Option[KVSValue] = {
        case Some(v) if v.isInstanceOf[KVSValue] ⇒ Some(v.asInstanceOf[KVSValue]) // 暗黙変換されるか心配
        case Some(v) ⇒
          log.error(s"Received is not Data: $v"); None
        case None ⇒ log.error(s"not found"); None
      }

      val key2chunk = (key: Seq[Byte]) ⇒ // Validationの利用？
        for {
          _ ← log.debug(s"finding chunk for: ${nodeID(key.toArray).getBase64}").point[Option]
          addr ← stateAgt().selfID >> Await.result(nd.findNode(nodeID(key.toArray)), 50 second).identifier
          //addr <- Tags.First(Await.result(nd.findNode(nodeID(key.toArray)), 50 second).idaddress) |+| Tags.First(stateAgt().selfID) // findNodeに失敗したらself
          _ ← log.debug(s"addr for key: $addr").point[Option]
          selfid ← stateAgt().selfID
          transmitter ← addr.getTransmitter.some
          chunk ← transmitter.getChunk(key)
        } yield chunk

      byte_meta match {
        case None ⇒ None
        case Some(MetaData(title, count, size, digests)) ⇒
          log.debug("Digests: " + digests.map(dg ⇒ nodeID(dg.toArray).getBase64).mkString("¥n"))
          val ensured = digests.map(key2chunk).map(KVSValueEnsuring)
          ensured.sequence match {
            case None ⇒ None
            case Some(valSeq: Stream[KVSValue]) ⇒ Some(valSeq.map {
              (v: KVSValue) ⇒ v.value
            }.flatten.toStream)
          }
        case Some(_) ⇒ None
      }
    }

  }

  /**
   * 現在のPredecessorと比較し、最適な状態になるべく調整します。
   * 渡された[[momijikawa.p2pscalaproto.NodeIdentifier]]と現在のPredecessorを比較し、原則として、より近い側をPredecessorとして決め、ノードの状態を更新します。
   * また、渡されたものと自分が同じノードであるときは変更しません。
   * また、現在のPredecessorが利用できないときには渡された[[momijikawa.p2pscalaproto.NodeIdentifier]]を受け入れます。
   * @param sender 比較対象のPredecessor。
   */
  def checkPredecessor(sender: NodeIdentifier)(implicit context: ActorContext) = {
    // selfがsaidではなく、指定した条件に合致した場合には交換の必要ありと判断する
    // 送信元がselfの場合は無視し、そうでないときは検査開始
    require(stateAgt().selfID.isDefined)

    // ----- //
    val check: (ChordState) ⇒ Boolean =
      (st) ⇒ allCatch.opt {
        context.system.log.info("ChordState: checking my predecessor")

        // when pred is self?
        val isSenderSelf = st.selfID.get.id == sender.id

        isSenderSelf match {
          case true ⇒ false // 必要無し
          case false ⇒
            val isPredEmpty = st.pred.isEmpty
            val isSaidNodeBetter = (st.pred map {
              pred ⇒
                {
                  sender.belongs_between(pred).and(st.selfID.get) ||
                    pred == st.selfID.get ||
                    st.succList.nearestSuccessor(st.selfID.get).id == st.selfID.get.id
                }
            }) | false // pred = Noneの場合も考慮
            val isPredDead = st.pred.map { _.getMessenger.checkLiving.unary_! } | false
            isPredEmpty ∨ isSaidNodeBetter ∨ isPredDead
        }
      } | false

    val execute: Boolean ⇒ (Agent[ChordState], NodeIdentifier) ⇒ Unit = {
      case true ⇒ (agt: Agent[ChordState], addr: NodeIdentifier) ⇒ {
        context.system.log.info("going to replace predecessor.")

        // 以前のpredがあればアンウォッチする
        //agt().pred map (ida => context.unwatch(ida.actorref))

        // predを変更する
        agt send {
          _.copy(pred = addr.some)
        }

        // succ=selfの場合、succも同時変更する
        if (agt().succList.nearestSuccessor(agt().selfID.get) == agt().selfID.get) {
          agt send {
            _.copy(succList = NodeList(List(addr)))
          }
        }
        agt().stabilizer.start()
        //context.watch(addr.actorref)
      }

      case false ⇒ (_: Agent[ChordState], _: NodeIdentifier) ⇒ // do nothing
    }

    atomic {
      implicit txn ⇒ (check >>> execute)(stateAgt())(stateAgt, sender)
    }
  }

}
