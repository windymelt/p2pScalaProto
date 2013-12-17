package momijikawa.p2pscalaproto

import scalaz._
import Scalaz._
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.ActorRef
import akka.agent.Agent

/**
 * FingerTableの安定化を行います。
 * このオブジェクトは直接動作を行いません。[[scalaz.State]]を返し、実際の動作は他所で行います。
 */
class FingerStabilizer(watcher: NodeWatcher) {

  /**
   * 安定化処理を生成します。[[momijikawa.p2pscalaproto.ChordState]]を渡すと[[momijikawa.p2pscalaproto.ChordState.fingerList]]中のランダムなノードを最適なノードに置き換えます。
   */
  val stabilize: ChordState => ChordState = (cs: ChordState) => {
    val fList = cs.fingerList.nodes.list
    val indexToUpdate = util.Random.nextInt(fList.size - 1) + 1 // 1 to size-1 (except for 0: 2^0)

    unwatch(fList(indexToUpdate).actorref)

    val newIdAddress: Option[idAddress] = getNewIdAddress(cs, indexToUpdate)

    val newList: List[idAddress] = {
      newIdAddress >>= {
        (id: idAddress) =>
          watch(id.actorref)
          fList.patch(indexToUpdate, Seq(id), 1).some
      }
    } | fList

    cs.copy(fingerList = NodeList(newList))
  }

  private def getNewIdAddress(cs: ChordState, idx: Int): Option[idAddress] = Await.result(cs.selfID.get.getClient(cs.selfID.get).findNode(new nodeID(BigInt(2).pow(idx).toByteArray)), 10 second).idaddress

  private def watch(a: ActorRef): Unit = watcher.watch(a)

  private def unwatch(a: ActorRef): Unit = watcher.unwatch(a)
}
