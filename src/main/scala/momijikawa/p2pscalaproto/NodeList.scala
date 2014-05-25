package momijikawa.p2pscalaproto

import akka.actor.ActorRef

/**
 * SuccessorTableやFingerTableに使われるノードのリストです。
 * 両者に共通の機能を実装しています。
 * @param nodes 初期ノード。
 */
case class NodeList(nodes: scalaz.NonEmptyList[NodeIdentifier]) {
  /**
   * 所与のノードにもっとも後ろから接近しているノードを返します。
   * @param objective 対象のノード。
   * @param self このノード。
   * @return 最接近しているノード。
   */
  def closestPrecedingNode(objective: TnodeID)(self: NodeIdentifier): NodeIdentifier = {
    nodes.list.reverse.find(p ⇒ p belongs_between self and objective).getOrElse(self)
  }
  /**
   * 所与のノードIDに最も近いものを返します。
   * @param id_query 検索の対象となるノードID。
   * @param id_self 自分のノードID。
   * @return 最近傍のノード。
   */
  def nearestNeighbor(id_query: TnodeID, id_self: TnodeID): NodeIdentifier = {
    nodes.list.filter(id_query.belongs_between(id_self).and(_))
      .minBy {
        distanceFrom(_) to id_query
      }
  }

  def nearestNeighborWithoutSelf(id_query: TnodeID, id_self: TnodeID): Option[NodeIdentifier] = {
    nodes.list.filterNot(_.id == nodeID(id_self.idVal)) match {
      case lis: List[NodeIdentifier] if lis.isEmpty ⇒ None
      case lis: List[NodeIdentifier] ⇒
        Some(lis.filter(id_query.belongs_between(id_self).and(_))
          .minBy {
            distanceFrom(_) to id_query
          }
        )
    }
  }

  /**
   * 所与のノードIDにSuccessorとして最も近いノードを返します。
   * @param id_self 検索の対象となるノードID。
   * @return 最近傍のSuccessor
   */
  def nearestSuccessor(id_self: TnodeID): NodeIdentifier =
    nodes.list.minBy(ida ⇒ TnodeID.leftArrowDistance(to = id_self, from = ida))

  def nearestSuccessorWithoutSelf(id_self: TnodeID): Option[NodeIdentifier] =
    nodes.list.filterNot(_.id == nodeID(id_self.idVal)) match {
      case lis: List[NodeIdentifier] if lis.isEmpty ⇒ None
      case lis: List[NodeIdentifier] ⇒
        Some(lis.minBy(ida ⇒ TnodeID.leftArrowDistance(to = id_self, from = ida)))
    }

  /**
   * 所与の番号のノード情報を削除した[[momijikawa.p2pscalaproto.NodeList]]を返します。
   * @param index 削除するノードの添字。
   * @return 所与のノードが削除された[[momijikawa.p2pscalaproto.NodeList]]
   */
  def remove(index: Int): NodeList = NodeList(nodes.list.take(index) ++ nodes.list.drop(index + 1))

  def remove(a: ActorRef): NodeList = NodeList(nodes.list.filterNot((i) ⇒ i.uri == a))

  /**
   * 所与のアクターを持つ[[momijikawa.p2pscalaproto.NodeIdentifier]]を書き換えます。
   * @param from 書き換え対象となる[[momijikawa.p2pscalaproto.NodeIdentifier]]が持つべきアクター。
   * @param to 更新に利用する[[momijikawa.p2pscalaproto.NodeIdentifier]]。
   * @return 書き換え済の[[momijikawa.p2pscalaproto.NodeList]]。
   */
  def replace(from: ActorRef, to: NodeIdentifier) = NodeList(nodes.list.map(p ⇒ if (p.uri == from) to else p))

  /**
   * 所与のインデックスにあるノードを他のノードと差し替えます。
   * @param index 差し替え対象となるインデックス。
   * @param replacement 差し替えるノード。
   * @return 差し替え済の[[momijikawa.p2pscalaproto.NodeList]]。
   */
  def patch(index: Int, replacement: NodeIdentifier) = {
    NodeList(nodes.list.patch(index, List(replacement), 1))
  }

  /**
   * 所与のインデックスに該当するノードを返します。
   * @param n インデックス。
   * @return 該当するノード。
   */
  def apply(n: Int): NodeIdentifier = nodes.list(n)

  /**
   * 所与のノードIDに最も近いノードを削除した[[momijikawa.p2pscalaproto.NodeList]]を返します。
   * @param id_self 検索の対象となるノードID。
   * @return 所与のノードの最近傍のノードが削除された[[momijikawa.p2pscalaproto.NodeList]]
   */
  def killNearest(id_self: TnodeID): NodeList = {
    val beKnocked = nodes.list.minBy(TnodeID.leftArrowDistance(id_self, _))
    NodeList(nodes.list.filterNot(_.id == beKnocked.id))
  }
}

/**
 * [[momijikawa.p2pscalaproto.NodeList]]を[[scala.collection.immutable.List]]から生成するためのシュガーシンタックス
 */
object NodeList {
  def apply(lis: List[NodeIdentifier]): NodeList = {
    import scalaz._
    import Scalaz._
    lis match {
      case Nil ⇒ throw new Exception("list should not be empty.")
      case xs  ⇒ NodeList(xs.toNel.get)
    }
  }
}
