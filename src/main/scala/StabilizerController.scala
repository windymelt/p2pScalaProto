package momijikawa.p2pscalaproto

import akka.actor._
import akka.pattern._
import scala.concurrent.Await
import scala.concurrent.duration._

class StabilizerController(stabilizerActor: ActorRef) {
  def status: Boolean = {
    implicit val timeout: akka.util.Timeout = 10 seconds;
    Await.result[Boolean](stabilizerActor.ask(StabilizeStatus).mapTo[Boolean], 10 seconds)
  }
  def start() = stabilizerActor ! StartStabilize
  def stop() = stabilizerActor ! StopStabilize
}
