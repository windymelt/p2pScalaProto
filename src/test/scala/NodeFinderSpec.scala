package momijikawa.p2pscalaproto.test

import org.specs2.mutable._
import momijikawa.p2pscalaproto._

class NodeFinderSpec extends Specification {
  val node0 = nodeID(BigInt(0).toByteArray)
  val node60 = nodeID(BigInt(2).pow(160) / 6 toByteArray)
  val node120 = nodeID(BigInt(2).pow(160) * 2 / 6 toByteArray)
  val node180 = nodeID(BigInt(2).pow(160) * 3 / 6 toByteArray)
  val node240 = nodeID(BigInt(2).pow(160) * 4 / 6 toByteArray)
  val node300 = nodeID(BigInt(2).pow(160) * 5 / 6 toByteArray)

  "NodeFinder" can {
    "自分の領域の場合は自分を返す: クエリと自分のIDが等しい場合" in {
      new NodeFinder[Boolean](node0, node0, node60, () => true, () => false, () => false).judge must beTrue
      new NodeFinder[Boolean](node60, node60, node120, () => true, () => false, () => false).judge must beTrue
      new NodeFinder[Boolean](node120, node120, node180, () => true, () => false, () => false).judge must beTrue
      new NodeFinder[Boolean](node180, node180, node240, () => true, () => false, () => false).judge must beTrue
      new NodeFinder[Boolean](node240, node240, node300, () => true, () => false, () => false).judge must beTrue
      new NodeFinder[Boolean](node300, node300, node0, () => true, () => false, () => false).judge must beTrue
    }
    "ちょうどSuccessorのときもSuccessorを返す" in {
      new NodeFinder[Boolean](node60, node0, node60, () => false, () => true, () => false).judge must beTrue
      new NodeFinder[Boolean](node120, node60, node120, () => false, () => true, () => false).judge must beTrue
      new NodeFinder[Boolean](node180, node120, node180, () => false, () => true, () => false).judge must beTrue
      new NodeFinder[Boolean](node240, node180, node240, () => false, () => true, () => false).judge must beTrue
      new NodeFinder[Boolean](node300, node240, node300, () => false, () => true, () => false).judge must beTrue
      new NodeFinder[Boolean](node0, node300, node0, () => false, () => true, () => false).judge must beTrue
    }
    "Successor領域の場合はSuccessorを返す: Successor(id)がidを担当する" in {
      new NodeFinder[Boolean](node60, node0, node120, () => false, () => true, () => false).judge must beTrue
      new NodeFinder[Boolean](node120, node60, node180, () => false, () => true, () => false).judge must beTrue
      new NodeFinder[Boolean](node180, node120, node240, () => false, () => true, () => false).judge must beTrue
      new NodeFinder[Boolean](node240, node180, node300, () => false, () => true, () => false).judge must beTrue
      new NodeFinder[Boolean](node300, node240, node0, () => false, () => true, () => false).judge must beTrue
      new NodeFinder[Boolean](node0, node300, node60, () => false, () => true, () => false).judge must beTrue
    }
    "自分の領域でもSuccessor領域でもない場合は転送する" in {
      new NodeFinder[Boolean](node120, node0, node60, () => false, () => false, () => true).judge must beTrue
      new NodeFinder[Boolean](node180, node60, node120, () => false, () => false, () => true).judge must beTrue
      new NodeFinder[Boolean](node240, node120, node180, () => false, () => false, () => true).judge must beTrue
      new NodeFinder[Boolean](node300, node180, node240, () => false, () => false, () => true).judge must beTrue
      new NodeFinder[Boolean](node0, node240, node300, () => false, () => false, () => true).judge must beTrue
      new NodeFinder[Boolean](node60, node300, node0, () => false, () => false, () => true).judge must beTrue
    }
    "Successor == selfのときはSelfを返す" in {
      new NodeFinder[Boolean](node60, node0, node0, () => true, () => false, () => false).judge must beTrue
      new NodeFinder[Boolean](node120, node60, node60, () => true, () => false, () => false).judge must beTrue
      new NodeFinder[Boolean](node180, node120, node120, () => true, () => false, () => false).judge must beTrue
      new NodeFinder[Boolean](node240, node180, node180, () => true, () => false, () => false).judge must beTrue
      new NodeFinder[Boolean](node300, node240, node240, () => true, () => false, () => false).judge must beTrue
      new NodeFinder[Boolean](node0, node300, node300, () => true, () => false, () => false).judge must beTrue
    }
  }
}
