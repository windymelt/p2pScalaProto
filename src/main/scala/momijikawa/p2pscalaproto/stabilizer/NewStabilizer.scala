package momijikawa.p2pscalaproto.stabilizer

import momijikawa.p2pscalaproto.networking.WatchableObject
import WatchableObject._
import momijikawa.p2pscalaproto.LoggerLikeObject._
import momijikawa.p2pscalaproto._

/**
 * 新型の安定化クラスです。動作が改良されています。
 * Chordの安定化アルゴリズムに従ってSuccessorのリストを更新し、[[momijikawa.p2pscalaproto.ChordState]]を返すためのクラスです。
 * @param watcher ノードのアクターをWatch/Unwatchできるクラス
 * @param logger ログを取るクラス
 */
class NewStabilizer(watcher: Watchable, logger: LoggerLike) {
  /**
   * 安定化処理の本体。
   */
  val stabilize: ChordState ⇒ ChordState = (state: ChordState) ⇒ {

    require(state.selfID.isDefined)

    val selfID = state.selfID.get
    val succID = state.succList.nearestSuccessor(selfID)

    new StabilizeStrategyFactory(state).select.stabilize()
  }
}
