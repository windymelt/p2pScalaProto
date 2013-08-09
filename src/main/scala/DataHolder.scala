package momijikawa.p2pscalaproto


/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/07/08
 * Time: 16:13
 * To change this template use File | Settings | File Templates.
 */
sealed trait KVSData

final case class KVSValue(value: Stream[Byte]) extends KVSData

final case class MetaData(title: String, chunkCount: BigInt, sizeByte: BigInt, digests: Stream[Seq[Byte]]) extends KVSData