package momijikawa.p2pscalaproto

import akka.agent.Agent
import scala.concurrent.{ TimeoutException, Await }
import scala.concurrent.duration._
import akka.actor.ActorSystem

class StateAgentController(agent: Agent[ChordState])(implicit val system: ActorSystem) {
  def saveData(key: Seq[Byte], value: KVSData): Option[Seq[Byte]] = {
    try {
      Await.result(agent.alter {
        state ⇒
          state.copy(dataholder = state.dataholder + ((key, value)))
      }, 30 seconds)
      Some(key)
    } catch {
      case e: TimeoutException ⇒ None // do nothing
    }
  }
}
