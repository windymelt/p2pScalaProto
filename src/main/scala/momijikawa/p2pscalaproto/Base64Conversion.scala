package momijikawa.p2pscalaproto

import org.apache.commons.codec.binary.Base64

object Base64Conversion {
  implicit class SeqByte2Base64Expression(val seq: Seq[Byte]) extends AnyVal {
    def base64: String = Base64.encodeBase64URLSafeString(seq.toArray)
  }
  implicit class Base64String2SeqByteExpression(val str: String) extends AnyVal {
    def base64Seq: Seq[Byte] = Base64.decodeBase64(str)
  }
}
