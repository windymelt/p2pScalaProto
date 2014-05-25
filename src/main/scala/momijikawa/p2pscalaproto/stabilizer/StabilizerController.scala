package momijikawa.p2pscalaproto.stabilizer

import akka.actor._
import akka.pattern._
import scala.concurrent.Await
import scala.concurrent.duration._
import momijikawa.p2pscalaproto.messages._

/**
 * 安定化の開始と停止を担当するクラス。
 * @param stabilizerActor 実際に安定化を行うアクター。
 */
class StabilizerController(stabilizerActor: ActorRef) {
  def status: Boolean = {
    implicit val timeout: akka.util.Timeout = 10 seconds;
    Await.result[Boolean](stabilizerActor.ask(StabilizeStatus).mapTo[Boolean], 10 seconds)
  }
  def start() = stabilizerActor ! StartStabilize
  def stop() = stabilizerActor ! StopStabilize
}
