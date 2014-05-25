package momijikawa.p2pscalaproto.networking

import akka.actor._

/**
 * ノードを監視するためのクラス。
 * @param context 必要な文脈情報。
 */
class NodeWatcher(context: ActorContext) {

  def watch(node: ActorRef): Unit = {
    context.watch(node)
  }

  def unwatch(node: ActorRef): Unit = {
    context.unwatch(node)
  }

}
