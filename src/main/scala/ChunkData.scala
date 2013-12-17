package momijikawa.p2pscalaproto

class ChunkData(chunk: Stream[Byte]) {

  import java.security.MessageDigest

  val digestFactory = MessageDigest.getInstance("SHA-1")

  def getBase64 = {
    digestFactory.digest(chunk.toArray)
  }
}
