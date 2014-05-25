package momijikawa.p2pscalaproto.networking

import akka.actor.ActorRef

/**
 * watch/unwatchを行うトレイト
 */
object WatchableObject {

  type Watchable = {
    def watch(subject: ActorRef): ActorRef
    def unwatch(subject: ActorRef): ActorRef
  }

}