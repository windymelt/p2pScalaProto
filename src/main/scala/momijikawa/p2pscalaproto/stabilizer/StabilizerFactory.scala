package momijikawa.p2pscalaproto.stabilizer

import akka.actor._
import momijikawa.p2pscalaproto.messages.Stabilize

class StabilizerFactory(context: ActorContext) {
  def generate(receiver: ActorRef) = {
    new StabilizerController(context.actorOf(Props(classOf[StabilizerBeacon], receiver, Stabilize, context.dispatcher), name = "Stabilizer"))
  }
}
