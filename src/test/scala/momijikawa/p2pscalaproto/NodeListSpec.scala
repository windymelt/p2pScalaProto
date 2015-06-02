package momijikawa.p2pscalaproto

import momijikawa.p2pscalaproto.nodeID
import org.specs2.mutable._
import momijikawa.p2pscalaproto._

class NodeListSpec extends Specification {

  import akka.actor._
  import akka.actor.ActorDSL._
  import scalaz._
  import Scalaz._

  implicit val system = ActorSystem(name = "testframe")

  val dummyActor = actor("dummy")(new Act {
    become {
      case anything => // do nothing
    }
  })

  //  val OMEGA = BigInt(2).pow(160)

  /** 0-1-2-3-4-5-6-7-_-_-_-_-0 */
  val nodes: List[idAddress] = (0 to 6) map {
    BigInt(_)
  } map {
    _.toByteArray
  } map {
    idAddress(_, dummyActor)
  } toList

  val nodelist: NodeList = NodeList(nodes)

  "remove" should {

    "remove-immutable" in {
      nodelist.remove(0).nodes.list(1) must_== nodelist.remove(0).nodes.list(1)
    }

    "removeするとsizeが減る" in {
      nodelist.remove(0).nodes.size must_== nodelist.nodes.size - 1
    }

    "0をremoveするとheadは1" in {
      nodelist.remove(0).nodes.head must_== nodelist.nodes.list(1)
    }

  }

  /*  "nearestNeighbor" should {

    "0のNearestNeighborは1" in {
      nodelist.remove(0).nearestNeighbor(nodelist.nodes.list(4), nodelist.nodes.list(0)).getNodeID.toBigInt must_== nodelist.nodes.list(1).getNodeID.toBigInt
    }
  }
 */

  "nearestSuccessor" should {

    "自分が含まれるリスト中では自分を返せる" in {
      nodelist.nearestSuccessor(nodelist.nodes.list(0)).asNodeID.toBigInt must_== nodelist.nodes.list(0).asNodeID.toBigInt
    }

    "0のNearestSuccessorは1" in {
      nodelist.remove(0).nearestSuccessor(nodelist.nodes.list(0)).asNodeID.toBigInt must_== nodelist.nodes.list(1).asNodeID.toBigInt
    }

    "3のNearestSuccessorは4" in {
      nodelist.remove(3).nearestSuccessor(nodelist.nodes.list(3)).asNodeID.toBigInt must_== nodelist.nodes.list(4).asNodeID.toBigInt
    }

    "6のNearestSuccessorは0" in {
      nodelist.remove(6).nearestSuccessor(nodelist.nodes.list(6)).asNodeID.toBigInt must_== nodelist.nodes.list(0).asNodeID.toBigInt
    }

  }

  "killNearest" should {
    "self=0のとき1をキルする" in {
      val nodelistWithout0 = NodeList(nodelist.nodes.tail)
      nodelistWithout0.killNearest(nodelist.nodes.list(0)).nodes.list must_== nodelistWithout0.nodes.tail
    }
  }

  /*  "sortedBySelfID" should {
    "3を除いたNodeListで3基準でソートすると4,5,6に並ぶ" in {
      nodelist.remove(3).sortedBySelfID(id_self = nodelist.nodes.list(3)).nodes.list.take(3).map{
        _.getNodeID.toBigInt
      } must_== List[BigInt](4, 5, 6)
    }
 }*/

  "NodeList constructor" should {
    "空のリストを渡すとエラーを吐く" in {
      NodeList(List()) must throwA[Exception](message = "list should not be empty.")
    }
  }

  "closestPreceedingNode" should {
    "期待通りのノードを返す: 担当するノードの直前のノードを選ぶ" in {
      val nodes: List[idAddress] = (0 to 100 by 2) map {
        BigInt(_)
      } map {
        _.toByteArray
      } map {
        idAddress(_, dummyActor)
      } toList

      val nodelist: NodeList = NodeList(nodes)

      for (n <- 1 to 99 by 2) {
        nodelist.closestPrecedingNode(new nodeID(BigInt(n).toByteArray))(idAddress(BigInt(n - 2).toByteArray, dummyActor)).asNodeID.toBigInt must_== BigInt(n - 1)
      }

      nodelist.closestPrecedingNode(new nodeID(BigInt(0).toByteArray))(idAddress(BigInt(99).toByteArray, dummyActor)).asNodeID.toBigInt must_== BigInt(100)
    }
  }
}
