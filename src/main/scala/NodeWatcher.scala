package momijikawa.p2pscalaproto

import akka.actor._

class NodeWatcher(context: ActorContext) {

  def watch(node: ActorRef) = {
    context.watch(node)
  }

  def unwatch(node: ActorRef) = {
    context.unwatch(node)
  }

}
