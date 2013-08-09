package momijikawa.p2pscalaproto

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

object App {
  // TODO: ChordのActor部分すなわちコアとそのインターフェースを作成せよ
  def main(args: Array[String]) {
    import scala.concurrent.ExecutionContext.global
    implicit val ec = global
    type -->[A, B] = PartialFunction[A, B]
    println("Hello momijikawa.P2PScalaProto!")
    val chord = new Chord
    chord.init(TnodeID.newNodeId)
    println("initialized")

    // MIME機能を付けろ
    val dummy = Random.alphanumeric.take(4096).map(_.toByte)
    val key = chord.put("wakimiko", dummy)
    val loaded = Await.result(key, 50 second).map {
      chord.get((_))
    }.get
    var gotten: Stream[Byte] = null
    //val loaded = key flatMap {(keyO: Option[Array[Byte]]) => chord.get(keyO.get)}
    val pfi: Option[Stream[Byte]] --> Unit = {
      case oSB => println(oSB.flatMap {
        bytes: Stream[Byte] => Some(String.copyValueOf(bytes.map {
          _.toChar
        }.toArray))
      }.getOrElse("FAILED"))
        gotten = oSB.get
        chord.close()
        println(gotten == dummy)
    }
    loaded.onSuccess(pfi)
  }
}
