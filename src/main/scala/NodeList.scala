package momijikawa.p2pscalaproto

import akka.actor.ActorRef

case class NodeList(nodes: scalaz.NonEmptyList[idAddress]) {
  /**
   * 所与のノードIDに最も近いものを返します。
   * @param id_query 検索の対象となるノードID。
   * @param id_self 自分のノードID。
   * @return 最近傍のノード。
   */
  def nearestNeighbor(id_query: TnodeID, id_self: TnodeID): idAddress = {
    nodes.list.filter(id_query.belongs_between(id_self).and(_))
      .minBy {
      distanceFrom(_) to id_query
    }
  }

  // TODO: 自分自身はどうするのか
  /**
   * 所与のノードIDにSuccessorとして最も近いノードを返します。
   * @param id_self 検索の対象となるノードID。
   * @return 最近傍のSuccessor
   */
  def nearestSuccessor(id_self: TnodeID): idAddress =
    nodes.list.minBy(ida => TnodeID.leftArrowDistance(to = id_self, from = ida))

  /**
   * 所与の番号のノード情報を削除した[[momijikawa.p2pscalaproto.NodeList]]を返します。
   * @param index 削除するノードの添字。
   * @return 所与のノードが削除された[[momijikawa.p2pscalaproto.NodeList]]
   */
  def remove(index: Int): NodeList = NodeList(nodes.list.take(index) ++ nodes.list.drop(index + 1))

  def remove(a: ActorRef): NodeList = NodeList(nodes.list.filterNot((i) => i.a == a))

  def replace(from: ActorRef, to: idAddress) = NodeList(nodes.list.map(p => if (p.a == from) to else p))

  /**
   * 所与のノードIDに最も近いノードを削除した[[momijikawa.p2pscalaproto.NodeList]]を返します。
   * @param id_self 検索の対象となるノードID。
   * @return 所与のノードの最近傍のノードが削除された[[momijikawa.p2pscalaproto.NodeList]]
   */
  def killNearest(id_self: TnodeID): NodeList = {
    val beKnocked = nodes.list.minBy(TnodeID.leftArrowDistance(id_self, _))
    NodeList(nodes.list.filterNot(_.id.deep == beKnocked.id.deep))
  }
}

/**
 * [[momijikawa.p2pscalaproto.NodeList]]を[[scala.collection.immutable.List]]から生成するためのシュガーシンタックス
 */
object NodeList {
  def apply(lis: List[idAddress]): NodeList = {
    import scalaz._
    import Scalaz._
    lis match {
      case Nil => throw new Exception("list should not be empty.")
      case xs => NodeList(xs.toNel.get)
    }
  }
}
