package momijikawa.p2pscalaproto

import akka.actor._

class StabilizerFactory(context: ActorContext) {
  def generate(receiver: ActorRef) = {
    context.actorOf(Props(classOf[Stabilizer], receiver, Stabilize, context.dispatcher), name = "Stabilizer")
  }
}
