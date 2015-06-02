package momijikawa.p2pscalaproto

import org.specs2.mutable._
import momijikawa.p2pscalaproto._

class TnodeIDSpec extends Specification {

  val OMEGA = BigInt(2).pow(160)
  val id_0 = nodeID(BigInt(0).toByteArray)
  val id_1000 = nodeID(BigInt(1000).toByteArray)
  val id_100000 = nodeID(BigInt(100000).toByteArray)

  "TnodeID" can {

    "equal" in {
      id_1000.equals(id_1000) must_== true
      id_1000.equals(nodeID(BigInt(1000).toByteArray)) must_== true
      id_1000.equals(id_100000) must_== false
      id_1000 == id_1000 must_== true
      id_1000 == nodeID(BigInt(1000).toByteArray) must_== true
    }

    "TnodeID objectのdistanceメソッドで距離計測ができる" in {
      TnodeID.distance(id1 = id_0, id2 = id_1000) must_== BigInt(1000)
      TnodeID.distance(id1 = id_1000, id2 = id_0) must_== BigInt(1000)
      TnodeID.distance(id1 = id_0, id2 = id_100000) must_== BigInt(100000)
      TnodeID.distance(id1 = id_1000, id2 = id_100000) must_== BigInt(99000)
    }

    "distanceFrom構文で距離計測ができる" in {
      distanceFrom(id_0) to id_1000 must_== BigInt(1000)
      distanceFrom(id_0) to id_100000 must_== BigInt(100000)
      distanceFrom(id_1000) to id_100000 must_== BigInt(99000)
    }

    "belongs関数で所属判定ができる" in {
      TnodeID.belongs(X = id_1000, alpha = id_0, omega = id_100000) must beTrue
      TnodeID.belongs(X = id_1000, alpha = id_100000, omega = id_0) must beFalse
    }

    "belongs_between構文で所属判定ができる" in {
      (id_1000 belongs_between id_0 and id_100000) must beTrue
      (id_1000 belongs_between id_100000 and id_0) must beFalse
      (id_0 belongs_between id_100000 and id_1000) must beTrue
      (id_100000 belongs_between id_1000 and id_0) must beTrue
      (id_100000 belongs_between id_0 and id_1000) must beFalse
    }

    "右端では反応する" in {
      (id_100000 belongs_between id_1000 and id_100000) must beTrue
    }

    "左端では反応しない" in {
      (id_0 belongs_between id_0 and id_1000) must beFalse
      (id_1000 belongs_between id_1000 and id_100000) must beFalse
    }

    "x == yのときは反応する" in {
      (id_0 belongs_between id_0 and id_0) must beTrue
    }

    "左向き距離が正しく計算できる" in {
      TnodeID.leftArrowDistance(to = id_0, from = id_1000) must_== BigInt(1000)
      TnodeID.leftArrowDistance(to = id_1000, from = id_0) must_== OMEGA - BigInt(1000)
      TnodeID.leftArrowDistance(to = id_1000, from = id_100000) must_== BigInt(99000)
      TnodeID.leftArrowDistance(to = id_100000, from = id_1000) must_== OMEGA - BigInt(99000)
    }

    "←構文を利用した左向き計算ができる" in {
      id_0 <----- id_1000 must_== BigInt(1000)
      id_1000 <----- id_100000 must_== BigInt(99000)
    }

    "左向き距離は同じノード間の演算に対しては0を返す" in {
      id_0 <----- id_0 must_== BigInt(0)
      id_1000 <----- id_1000 must_== BigInt(0)
    }
  }

  "fingerIdx2nodeID" should {
    "範囲外引数でエラー" in {
      TnodeID.fingerIdx2NodeID(-1)(new nodeID(BigInt(0).toByteArray)) must throwA[IllegalArgumentException]
      TnodeID.fingerIdx2NodeID(160)(new nodeID(BigInt(0).toByteArray)) must throwA[IllegalArgumentException]
    }

    "公式に合う答えを返す" in {
      import scala.util.Random
      val rnd = new Random()
      for (k <- 0 to 159) {
        val self = new nodeID(BigInt(2).pow(rnd.nextInt(159)) toByteArray)
        val node = TnodeID.fingerIdx2NodeID(k)(self)
        BigInt(1, node.bytes) must_== ((BigInt(self.bytes) + BigInt(2).pow(k + 1 - 1)) mod BigInt(2).pow(160))
      }
    }
  }
}
