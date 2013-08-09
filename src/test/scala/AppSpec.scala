package momijikawa.p2pscalaproto.test

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
}
