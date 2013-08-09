package momijikawa.p2pscalaproto

import scalaz._
import Scalaz._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/07/25
 * Time: 2:59
 * To change this template use File | Settings | File Templates.
 */
object fingerStabilizer {
  val stabilize = State[ChordState, ChordState] {
    (cs: ChordState) =>
      val updatedIndex = util.Random.nextInt(cs.fingerList.size - 1) + 1 // 1 to size-1 (except for 0: 2^0)
    val updatedIdAddress: Option[idAddress] = Await.result(cs.selfID.get.getClient(cs.selfID.get).findNode(new nodeID(BigInt(2).pow(updatedIndex).toByteArray)), 10 second).idaddress
      val pair = cs.fingerList.splitAt(updatedIndex)
      val newList: List[idAddress] = {
        updatedIdAddress >>= {
          (id: idAddress) => (pair._1 ++ (id :: pair._2.tail)).some
        }
      } getOrElse (cs.fingerList)
      val newcs = cs.copy(fingerList = newList)
      (newcs, newcs)
  }
}
