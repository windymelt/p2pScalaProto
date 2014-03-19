package momijikawa.p2pscalaproto.test

import momijikawa.p2pscalaproto.IdAddressMessage
import momijikawa.p2pscalaproto.nodeID
import org.specs2.mutable._
import org.specs2.mock._
import org.specs2.matcher._
import momijikawa.p2pscalaproto._
import scala.concurrent.Future
import akka.actor.ActorDSL._
import scala.Some
import akka.agent.Agent
import scala.collection.immutable.HashMap
import akka.actor.ActorSystem
import java.util.concurrent.TimeoutException
import org.specs2.ScalaCheck
import org.scalacheck._
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class fingerStabilizerSpec extends Specification with Mockito with ScalaCheck {
  sequential

  implicit val system = ActorSystem("fingerStabilizerSpec")

  val dummyActor = actor("dummy")(new Act {
    become {
      case anything => // do nothing
    }
  })

  "stabilizeOn" should {
    "findNodeに成功した場合正常に動作する" in {
      forAll(Gen.choose(0, 159)) {
        (testingIndex: Int) =>
          //(testingIndex >= 0 && testingIndex < 160) ==>
          {
            println(s"Testing for $testingIndex")
            val self = idAddress(BigInt(0).toByteArray, dummyActor)
            val csMock = spy(ChordState(
              selfID = Some(self),
              succList = NodeList(List(self)),
              fingerList = NodeList(List.fill(160)(self)),
              pred = None,
              dataholder = new HashMap[Seq[Byte], KVSData](),
              null
            ))
            val agent = Agent(csMock)(system.dispatcher)
            val watcherMock = mock[NodeWatcher]
            watcherMock.watch(any) answers { _ => }
            watcherMock.unwatch(any) answers { _ => }

            class finsta2(w: NodeWatcher, a: Agent[ChordState]) extends FingerStabilizer(w, a) {
              override def fetchNode(idx: Int) = Future.successful(Some(idAddress(TnodeID.fingerIdx2NodeID(testingIndex)(self.asNodeID).bytes, dummyActor)))
            }

            val stabilizer = new finsta2(watcherMock, agent)

            stabilizer.stabilizeOn(testingIndex)
            Thread.sleep(100) // waiting for apply to agent
            agent.get().fingerList.nodes.list(testingIndex).asNodeID.toBigInt must_== TnodeID.fingerIdx2NodeID(testingIndex)(self.asNodeID).toBigInt
          }
      }
    }

    "findNodeに失敗したときは変更しない" in {
      forAll(Gen.choose(0, 159)) {
        (testingIndex: Int) =>
          {
            val self = idAddress(BigInt(0).toByteArray, dummyActor)
            val csMock = spy(ChordState(
              selfID = Some(self),
              succList = NodeList(List(self)),
              fingerList = NodeList(List.fill(160)(self)),
              pred = None,
              dataholder = new HashMap[Seq[Byte], KVSData](),
              null
            ))
            val agent = Agent(csMock)(system.dispatcher)
            val watcherMock = mock[NodeWatcher]
            watcherMock.watch(any) answers { _ => }
            watcherMock.unwatch(any) answers { _ => }

            class finsta2(w: NodeWatcher, a: Agent[ChordState]) extends FingerStabilizer(w, a) {
              override def fetchNode(idx: Int) = Future.failed(new TimeoutException("meow meow meow"))
            }

            val stabilizer = new finsta2(watcherMock, agent)

            val oldList = agent().fingerList.nodes.list
            stabilizer.stabilizeOn(testingIndex)
            agent.get().fingerList.nodes.list must_== oldList
          }
      }
    }

    "findNodeに失敗したときは変更しない(None case)" in {
      forAll(Gen.choose(0, 159)) {
        (testingIndex: Int) =>
          {
            val self = idAddress(BigInt(0).toByteArray, dummyActor)
            val csMock = spy(ChordState(
              selfID = Some(self),
              succList = NodeList(List(self)),
              fingerList = NodeList(List.fill(160)(self)),
              pred = None,
              dataholder = new HashMap[Seq[Byte], KVSData](),
              null
            ))
            val agent = Agent(csMock)(system.dispatcher)
            val watcherMock = mock[NodeWatcher]
            watcherMock.watch(any) answers { _ => }
            watcherMock.unwatch(any) answers { _ => }

            class finsta2(w: NodeWatcher, a: Agent[ChordState]) extends FingerStabilizer(w, a) {
              override def fetchNode(idx: Int) = Future.successful(None)
            }

            val stabilizer = new finsta2(watcherMock, agent)

            val oldList = agent().fingerList.nodes.list
            stabilizer.stabilizeOn(testingIndex)
            agent.get().fingerList.nodes.list must_== oldList
          }
      }
    }
  }
}
