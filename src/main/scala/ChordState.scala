package momijikawa.p2pscalaproto

import scalaz._
import Scalaz._
import akka.actor._
import Utility.Utility._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.immutable.HashMap

/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/07/13
 * Time: 20:24
 * To change this template use File | Settings | File Templates.
 */
case class ChordState(
                       selfID: Option[idAddress],
                       succList: List[idAddress],
                       fingerList: List[idAddress],
                       pred: Option[idAddress],
                       dataholder: HashMap[Seq[Byte], KVSData],
                       stabilizer: ActorRef /*,
                       transmitter: ActorRef*/
                       ) {

  def copyS(
             selfId: Option[idAddress] = selfID,
             succList: List[idAddress] = succList,
             fingerList: List[idAddress] = fingerList,
             pred: Option[idAddress] = pred,
             dataholder: HashMap[Seq[Byte], KVSData] = dataholder,
             stabilizer: ActorRef = stabilizer /*,
             transmitter: ActorRef = transmitter*/
             ): State[ChordState, Unit] = {
    State[ChordState, Unit]((cs) => (this.copy(selfId, succList, fingerList, pred, dataholder, stabilizer /*, transmitter*/), Unit))
  }
}

object ChordState {
  type dataMap = HashMap[Seq[Byte], Seq[Byte]]
  type idListT = List[idAddress]

  sealed trait SL_FL

  final case object SL extends SL_FL

  final case object FL extends SL_FL


  /*def dataPut(key: Array[Byte], value: Array[Byte])(cs: ChordState): State[ChordState, Array[Byte]]/*Option[Array[Byte]]*/ = {
    case cs: ChordState => (cs.copy(dataholder = cs.dataholder.+ ((key, value))), key)
  }*/

  def dataPut(key: Seq[Byte], value: KVSData)(implicit context: ActorContext): State[ChordState, Seq[Byte]] =
    State[ChordState, Seq[Byte]] {
      (cs: ChordState) =>
        context.system.log.debug("putting data into this node.")
        (cs.copy(dataholder = cs.dataholder + ((key, value))), key)
    }

  val idListClear = (_: idListT) => List[idAddress]()
  val idListPush = (one: idAddress) => (idl: idListT) => one :: idl
  val idListRemove = (one: idAddress) => (idl: idListT) => idl.dropWhile(_ == one)

  val idListKillhead = (idl: idListT) => idl.reverse.tail.reverse // [1,2,3] => [1,2]

  def idListAct(which: SL_FL, f: idListT => idListT): State[ChordState, Unit] = State[ChordState, Unit] {
    case cs: ChordState =>
      which match {
        case SL => (cs.copy(succList = f(cs.succList)), Unit)
        case FL => (cs.copy(fingerList = f(cs.fingerList)), Unit)
      }
  }

  val predChange: (idAddress) => State[ChordState, Unit] = (newPred: idAddress) =>
    State[ChordState, Unit] {
      cs: ChordState => (cs.copy(pred = Some(newPred)), Unit)
    }

  def nearestNeighbor(idl: idListT, id_query: TnodeID, id_self: TnodeID): idAddress = {
    idl.filter(TnodeID.belongs(_, id_self, id_query)) minBy {
      _ <-> id_query
    }
  }

  val join: (idAddress, ActorRef) => State[ChordState, Boolean] = (connectTo: idAddress, stabilizer: ActorRef) =>
    State[ChordState, Boolean] {
      (cs: ChordState) =>
        Await.result(connectTo.getClient(cs.selfID.get).findNode(cs.selfID.get), 10 second).idaddress match {
          case Some(newsucc) =>
            def proc: State[ChordState, Unit] = {
              for {
                st <- get[ChordState]
                st2 <- st.copyS(succList = List[idAddress](newsucc), pred = None) //put[ChordState](st.copy(succList = Stack[idAddress](newsucc), pred = None))
                _ <- pass(stabilizer ! StartStabilize)
                _ <- pass(println("Joinに成功しました")) //pass(println("Joinに成功しました"))
                st3 <- get[ChordState]
              } yield st3
            }

            (proc.run(cs)._1, true)

          case None =>
            System.out.println("Selfに接続できません")
            (cs, false)
        }
    }

  /*  val findNodeCore: (ChordState) => (TnodeID) => Option[idAddress] = {
      (cs: ChordState) =>
        (id: TnodeID) =>
          val id_query = id
          val id_succ = cs.succList.last.some
          (id_query.some |@| id_succ) {
            _ == _
          } match {
            case Some(true) => cs.selfID
            case _ =>
              TnodeID.belongs(id_query, cs.selfID.get, id_succ.get) match {
                case true =>
                  val cli_succ = cs.succList.last.getClient(cs.selfID.get)
                  cli_succ.whoAreYou
                case false => // passing
                  ChordState.nearestNeighbor(cs.succList ++ cs.fingerList, id_query, cs.selfID.get).getClient(cs.selfID.get).findNode(id_query)
              }
          }
    }
  */
  def findNodeCoreS(cs: ChordState, id_query: TnodeID)(implicit context: ActorContext) = {
    context.system.log.debug("Received: findNodeCoreS")
    val id_succ = cs.succList.last.some
    (id_query.some |@| id_succ) {
      _.getArray().deep == _.getArray().deep
    } match {
      case Some(true) => context.sender ! IdAddressMessage(cs.selfID)
      case _ =>
        TnodeID.belongs(id_query, cs.selfID.get, id_succ.get) match {
          case true =>
            val cli_succ = cs.succList.last.getClient(cs.selfID.get)
            context.system.log.debug("Node found")
            context.sender ! IdAddressMessage(cli_succ.whoAreYou)
          case false => // passing
            context.system.log.debug("going to forward Findnode request to nearest node.")
            ChordState.nearestNeighbor(cs.succList ++ cs.fingerList, id_query, cs.selfID.get).getClient(cs.selfID.get).findNodeShort(id_query)
        }
    }

  }

  val yourSuccessorCore: (ChordState) => Option[idAddress] = (cs: ChordState) => cs.succList.last.some

  val yourPredecessorCore: (ChordState) => Option[idAddress] = {
    (cs: ChordState) =>
      cs.pred match {
        case Some(pred) =>
          cs.stabilizer ! StartStabilize
          // ここで死活の確認もしたほうがいいかも
          cs.pred
        case None =>
          None
      }
  }

  /**
   * idAddressを受け取り、現在のPredecessorと比較し、Predecessorをより望ましい方に変更します。
   */
  val checkPredecessor = (saidIDAddress: idAddress) => State[ChordState, ChordState] {
    (cs: ChordState) =>

      val self_said_NEQ = (cs.selfID |@| saidIDAddress.some) {
        (_: idAddress) == /*≠*/ (_: idAddress)
      } getOrElse false
      val predIsNone = cs.pred.isEmpty
      val saidBelongsBetter = TnodeID.belongs(saidIDAddress, cs.pred.get, cs.selfID.get)
      val predIsDead = !successorStabilizationFactory.checkPredLiving(cs)
      // このへん

      self_said_NEQ ∧ (predIsNone ∨ saidBelongsBetter ∨ predIsDead) match {
        case true =>
          System.out.println("Predecessorに変更: " + saidIDAddress.getBase64)
          val cs2 = cs.copy(pred = saidIDAddress.some) //Some(saidIDAddress) //monadic assist
          (cs2, cs2)
        case false =>
          (cs, cs)
      }
  }
}