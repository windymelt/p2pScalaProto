package momijikawa.p2pscalaproto

import scalaz._
import Scalaz._
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.ActorContext

/**
 * FingerTableの安定化を行います。
 * このオブジェクトは直接動作を行いません。[[scalaz.State]]を返し、実際の動作は他所で行います。
 */
class fingerStabilizer(context: ActorContext) {

  /**
   * 安定化処理を生成します。[[momijikawa.p2pscalaproto.ChordState]]を渡すと[[momijikawa.p2pscalaproto.ChordState.fingerList]]中のランダムなノードを最適なノードに置き換えます。
   */
  val stabilize = State[ChordState, ChordState] {
    (cs: ChordState) =>
      val updatedIndex = util.Random.nextInt(cs.fingerList.nodes.size - 1) + 1 // 1 to size-1 (except for 0: 2^0)
    val discardedActor = cs.fingerList.nodes.list(updatedIndex).actorref
      context.unwatch(discardedActor)
      val updatedIdAddress: Option[idAddress] = Await.result(cs.selfID.get.getClient(cs.selfID.get).findNode(new nodeID(BigInt(2).pow(updatedIndex).toByteArray)), 10 second).idaddress
      val pair = cs.fingerList.nodes.list.splitAt(updatedIndex)
      val newList: List[idAddress] = {
        updatedIdAddress >>= {
          (id: idAddress) =>
            context.watch(id.actorref)
            (pair._1 ++ (id :: pair._2.tail)).some
        }
      } getOrElse (cs.fingerList.nodes.list)
      val newcs = cs.copy(fingerList = NodeList(newList))
      (newcs, newcs)
  }
}
