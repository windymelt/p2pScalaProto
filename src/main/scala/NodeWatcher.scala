package momijikawa.p2pscalaproto

import akka.actor._

class NodeWatcher(context: ActorContext) {

  def watch(node: ActorRef): Unit = {
    context.watch(node)
  }

  def unwatch(node: ActorRef): Unit = {
    context.unwatch(node)
  }

}
