package momijikawa.p2pscalaproto.messages

import akka.actor._
import akka.pattern.ask
import scala.reflect.ClassTag
import akka.util.Timeout
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import momijikawa.p2pscalaproto._

/**
 * すべてのメッセージの継承元。
 */
class Message {
  // not sealed because of extension
  def !?[T: ClassTag](a: ActorRef): T = {
    implicit val timeout = Timeout(50 second)
    val f: Future[T] = (a ? this).mapTo[T]
    Await.result(f, 50 second)
  }
}

/**
 * ノード間で取り交わされるメッセージ。
 */
class nodeMessage extends Message

/**
 * 死活確認に利用されるメッセージ。
 */
case object Ping extends nodeMessage

/**
 * ノード情報を取得するメッセージ。
 */
case object WhoAreYou extends nodeMessage

/**
 * ノード検索に用いるメッセージ。
 */
case class FindNode(queryID: String) extends nodeMessage

/**
 * ノードのPredecessorを取得する際に用いるメッセージ。
 */
case object YourPredecessor extends nodeMessage

/**
 * ノードのSuccessorを取得する際に用いるメッセージ。
 */
case object YourSuccessor extends nodeMessage

/**
 * ノードがPredecessorであることを通知して相手のPredecessor情報を修正してもらう際に用いるメッセージ。
 * @param id Predecessorであるはずのノードの情報。
 */
case class AmIPredecessor(id: NodeIdentifier) extends nodeMessage

/**
 * ノードからデータを取得する際に用いるメッセージ。
 * @param id データのキー。
 */
case class GetChunk(id: Seq[Byte]) extends nodeMessage

/**
 * データをノードに挿入する際に用いるメッセージ。
 * @param id 挿入するデータのキー。
 * @param kvp 挿入するデータ。
 */
case class SetChunk(id: Seq[Byte], kvp: KVSData) extends nodeMessage

/**
 * [[momijikawa.p2pscalaproto.NodeIdentifier]]を返す際に用いるカプセル。
 * @param identifier
 */
case class NodeIdentifierMessage(identifier: Option[NodeIdentifier]) extends nodeMessage

/**
 * pingの返答として用いるメッセージ。
 * @param id_base64 返答するノードのID。
 */
case class PingACK(id_base64: String) extends nodeMessage

/**
 * 請求に応じてデータを返す際に用いるメッセージ。
 * @param id データのキー。
 * @param Value データ。
 */
case class ChankReturn(id: Seq[Byte], Value: KVSData) extends nodeMessage

/**
 * ノードの増減により所属が変更となるデータをまとめて移動させる場合に用いるメッセージ。
 * @param data データ群。
 */
case class Immigration(data: scala.collection.immutable.HashMap[Seq[Byte], KVSData]) extends nodeMessage

/**
 * ノードの安定化に用いるメッセージ。
 */
class stabilizeMessage extends Message

/**
 * 安定化の開始を指示するメッセージ。
 */
case object StartStabilize extends stabilizeMessage

/**
 * 安定化の停止を指示するメッセージ。
 */
case object StopStabilize extends stabilizeMessage

/**
 * 安定化の状況を請求するメッセージ。
 */
case object StabilizeStatus extends stabilizeMessage

/**
 * ユーザからChordに向かって送信されるメッセージ。
 */
class chordMessage extends Message

/**
 * 安定化処理を指示するメッセージ。
 */
case object Stabilize extends chordMessage

/**
 * ノードの初期化を指示するメッセージ。
 * @param id このノードに与えるID。
 */
case class InitNode(id: nodeID) extends chordMessage

/**
 * DHTへのデータ挿入を指示するメッセージ。
 * @param title IDのようなもの。
 * @param value データ。
 */
case class PutData(title: String, value: Stream[Byte]) extends chordMessage
sealed trait PutDataResult extends chordMessage
case class PutDataSuccessful(key: Seq[Byte]) extends PutDataResult
case class PutDataFailed(reason: String) extends PutDataResult

/**
 * DHTからのデータ取得を指示するメッセージ。
 * @param key キー。
 */
case class GetData(key: Seq[Byte]) extends chordMessage
sealed trait GetDataResult extends chordMessage
case class GetDataSuccessful(key: Seq[Byte], value: Seq[Byte]) extends GetDataResult
case class GetDataFailed(reason: String) extends GetDataResult

/**
 * DHTネットワークへの参加を指示するメッセージ。
 * @param connectTo 最初に接続するブートストラップノード。
 */
case class JoinNode(connectTo: NodeIdentifier) extends chordMessage

/**
 * ノードの状態を請求するメッセージ。
 */
case object GetStatus extends chordMessage

case object Serialize extends chordMessage

/**
 * ノードを終了させるメッセージ。
 */
case object Finalize extends chordMessage

/**
 * 一般的な応答を意味するメッセージ。
 */
case object ACK extends chordMessage
