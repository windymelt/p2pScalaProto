package momijikawa.p2pscalaproto.test

import org.specs2.mutable._
import momijikawa.p2pscalaproto._

class StabilizationStrategiesSpec extends Specification {

  "failableRecursiveList" should {

    "単純な0..9のリスト" in {
      Utility.failableRecursiveList[Int](i => Some(i + 1), Some(0), 10) must_== Some(List[Int](0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
    }

    "リストから要素を取り出してリストを再構成する" in {
      import util.control.Exception.allCatch
      val lis = List[Int](1, 3, 5)
      val f = (l: List[Int]) => allCatch opt (l.tail)
      Utility.failableRecursiveList(f, Some(lis), 5) must_== Some(List(List(1, 3, 5), List(3, 5), List(5), List()))
    }

  }
}
