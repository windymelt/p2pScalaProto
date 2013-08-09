package momijikawa.p2pscalaproto

import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.{Await, Future}
import akka.pattern.ask
import scala.reflect.ClassTag
import scala.concurrent.duration._
import scalaz._

/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/07/08
 * Time: 1:17
 * To change this template use File | Settings | File Templates.
 */
object Utility {

  object Utility {

    import scala.util.Random

    val r = new Random()


    def sandwitch[A, B, C, D <% Iterable[A], E <% Iterable[B]](f: (A => B => C))(x: D)(y: E): Seq[C] = {
      x.zip(y).map {
        (t: (A, B)) => f(t._1)(t._2)
      }.toSeq
    }

    def randomWeight(): Double = {
      val count: Int = r.nextInt(5)
      var a: Double = 0.0
      for (_ <- 0 to count) {
        a = r.nextGaussian
      }
      a
    }

    def tee[A, B](x: A)(f: (A) => _): A = {
      f(x)
      x
    }

    /*implicit class actorRefExtension(val a: ActorRef) extends AnyVal {
      def !?[T: ClassTag](m: Message) = {
        //val c = implicitly[ClassTag[T]]
        implicit val timeout = Timeout(5 second)
        val f: Future[T] = (a ? m).mapTo[T]
        Await.result(f, 5 second)
      }
    }*/
    /*implicit def callAndResponse(a: ActorRef) = new {
      def !?[T: ClassTag](m: Message) = {
        //val c = implicitly[ClassTag[T]]
        implicit val timeout = Timeout(5 second)
        val f: Future[T] = (a ? m).mapTo[T]
        Await.result(f, 5 second)
      }
    }*/

    def !?[T: ClassTag](m: Message)(a: ActorRef): T = {
      implicit val timeout = Timeout(5 second)
      val f: Future[T] = (a ? m).mapTo[T]
      Await.result(f, 50 second)
    }

    def pass[T](f: Any): State[T, Unit] = State[T, Unit] {
      (x: T) =>
        (x, Unit)
    }

    /*implicit def arrPlus(xs: Iterable[Double]) = new {
      def <+> : (Iterable[Double]) => Seq[Double] = (lis: Iterable[Double]) => lis.zip(xs).map {
        (t: Tuple2[Double, Double]) => t._1 + t._2
      }.toIndexedSeq.toSeq
    }

    implicit def arrSubt(xs: Iterable[Double]) = new {
      def <-> : (Iterable[Double]) => Seq[Double] = (lis: Iterable[Double]) => lis.zip(xs).map {
        (t: Tuple2[Double, Double]) => t._1 - t._2
      }.toIndexedSeq.toSeq
    }

    implicit def arrMult(xs: Iterable[Double]) = new {
      def <*> : (Iterable[Double]) => Seq[Double] = (lis: Iterable[Double]) => lis.zip(xs).map {
        (t: Tuple2[Double, Double]) => t._1 * t._2
      }.toIndexedSeq.toSeq
    }
*/
  }

}
