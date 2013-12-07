package momijikawa.p2pscalaproto

import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.{Await, Future}
import akka.pattern.ask
import scala.reflect.ClassTag
import scala.concurrent.duration._
import scalaz._

object Utility {

  object Utility {

    import scala.util.Random

    val r = new Random()


    def sandwitch[A, B, C, D <% Iterable[A], E <% Iterable[B]](f: (A => B => C))(x: D)(y: E): Seq[C] = {
      x.zip(y).map {
        (t: (A, B)) => f(t._1)(t._2)
      }.toSeq
    }

    def tee[A](x: A)(f: (A) => _): A = {
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
      implicit val timeout = Timeout(50 second)
      val f: Future[T] = (a ? m).mapTo[T]
      Await.result(f, 50 second)
    }

    def pass[T](f: Any): State[T, Unit] = State[T, Unit] {
      (x: T) =>
        (x, Unit)
    }

    def when(condition: => Boolean)(block: => Any): Unit = if (condition) block

    def when[T](condition: => Boolean)(trueBlock: => T) = new {
      def otherwise(falseBlock: => T): T = if (condition) {
        trueBlock
      } else {
        falseBlock
      }
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

  def failableRecursiveList[A](function: A => Option[A], initial: Option[A], times: Int): Option[List[A]] = {
    def internalRecursive(answer: A, function: A => Option[A], times: Int): List[A] = {
      times match {
        case 0 => Nil
        case n if n < 0 => Nil
        case n =>
          function(answer) match {
            case None => Nil
            case Some(sth) => sth :: internalRecursive(sth, function, n - 1)
          }
      }
    }

    initial match {
      case None => None
      case Some(sth) => Some(sth :: internalRecursive(sth, function, times - 1))
    }
  }

}
