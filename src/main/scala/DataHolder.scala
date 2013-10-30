package momijikawa.p2pscalaproto

/** DHTに保存するデータのラッパー */
sealed trait KVSData

final case class KVSValue(value: Stream[Byte]) extends KVSData

/** データを分割保存するために必要 */
final case class MetaData(title: String, chunkCount: BigInt, sizeByte: BigInt, digests: Stream[Seq[Byte]]) extends KVSData