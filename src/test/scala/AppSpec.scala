package momijikawa.p2pscalaproto.test

// てすとのてすと

import org.specs2.mutable._

class AppSpec extends Specification {

  "The 'Hello world' string" should {
    "contain 11 characters" in {
      "Hello world" must have size (11)
    }
    "start with 'Hello'" in {
      "Hello world" must startWith("Hello")
    }
    "end with 'world'" in {
      "Hello world" must endWith("world")
    }
  }

  "ActorSystem" should {

    "can copy and keep its reference" in {
      import akka.actor.ActorSystem
      val system = ActorSystem("ChordCore-DHT")
      val system2 = system
      (system == system2) must beTrue
    }
  }

  "Agents" should {
    import akka.agent.Agent
    import akka.actor.ActorSystem
    import scala.concurrent.duration._
    import akka.util.Timeout
    import scala.concurrent.stm._
    implicit val system = ActorSystem("agentTest")

    "agent normal" in {
      val agt1 = Agent(1)
      agt1 send (_ * 2);
      agt1.await(1000) === 2
    }

    "agent can be passed as val" in {
      val agt1 = Agent(1)
      val agentMultiplier = (a: Agent[Int]) => a send (_ * 2)
      val resultV: Int = { agentMultiplier(agt1); agt1.await(1000) }
      resultV === 2
    }
  }
}

