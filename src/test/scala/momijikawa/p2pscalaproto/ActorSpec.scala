package momijikawa.p2pscalaproto

import org.specs2.mutable._
import akka.actor._

class dummyActor extends Actor {
  def receive = {
    case everything => println(everything)
  }

  override def postStop = {
    println("whoooa!! I'm dying!!")
    super.postStop
  }
}

class dummyWatcher extends Actor {
  def receive = {
    case ('watch, a: ActorRef) => {
      println(s"now watching $a")
      context.watch(a)
    }
    case ('unwatch, a: ActorRef) => context.unwatch(a)
    case Terminated(a: ActorRef) => println(s"$a has been terminated")
  }
}

class ActorSpec extends Specification {

  "Actors" should {
    val system = ActorSystem("testsystem")
    val dummy = system.actorOf(Props[dummyActor], "dummy")
    val watcher = system.actorOf(Props[dummyWatcher], "watcher")
    watcher ! ('watch, dummy)
    dummy ! "hogehoge"
    dummy ! PoisonPill
    Thread.sleep(1000)
    watcher ! PoisonPill
    Thread.sleep(1000)
    system.shutdown()
    system.awaitTermination()
    1 must_== 1
  }

}
