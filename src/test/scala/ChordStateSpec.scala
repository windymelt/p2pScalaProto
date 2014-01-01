package momijikawa.p2pscalaproto.test

import momijikawa.p2pscalaproto._
import org.specs2.mutable._
import org.specs2.specification.BeforeExample
import akka.actor.ActorSystem
import akka.actor.ActorDSL._
import akka.agent.Agent
import scala.collection.immutable.HashMap
import scala.concurrent.Await
import org.specs2.time.NoTimeConversions

class ChordStateSpec extends Specification with BeforeExample with NoTimeConversions {
  sequential

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val system = ActorSystem("chordstatespec")

  val dummyActor = actor("dummy")(new Act {
    become {
      case anything => // do nothing
    }
  })

  val state = Agent(
    new ChordState(
      None,
      NodeList(List(idAddress(Array.fill(20)(0.toByte), dummyActor))),
      NodeList(List.fill(10)(idAddress(Array.fill(20)(0.toByte), dummyActor))),
      None,
      new HashMap[Seq[Byte], KVSData](),
      null
    )
  )

  def before = {
    state send {
      new ChordState(
        None,
        NodeList(List(idAddress(Array.fill(20)(0.toByte), dummyActor))),
        NodeList(List(idAddress(Array.fill(20)(0.toByte), dummyActor))),
        None,
        new HashMap[Seq[Byte], KVSData](),
        null)
    } // TODO
  }

  "dataput" should {
    "put and get correctly" in {
      import scala.concurrent.stm._
      import scala.concurrent.duration._
      assume(Stream[Byte](1, 2, 3) == Stream[Byte](1, 2, 3))
      val key = Seq[Byte](4, 5, 4, 5, 0, 7, 2, 1, 6, 7, 4, 1, 7, 1, 2, 3, 4, 5, 6, 7)
      val data = KVSValue(Stream[Byte](1, 2, 3, 4, 5))
      ChordState.putDataToNode(key, data, state)
      Await.result(state.future(), 1 second)
      println(state().dataholder)
      state().dataholder.get(key) must_== Some(KVSValue(Stream[Byte](1, 2, 3, 4, 5)))
    }
  }

}

