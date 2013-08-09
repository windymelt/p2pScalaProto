package momijikawa.p2pscalaproto

import com.sun.org.apache.xml.internal.security.utils.Base64
import akka.actor._
import scalaz._
import Scalaz._
import scala.util.Random

/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/07/08
 * Time: 1:21
 * To change this template use File | Settings | File Templates.
 */
case class idAddress(id: Array[Byte], a: ActorRef) extends TnodeID with TActorRef {
  override val idVal: Array[Byte] = id
  override val actorref = a

  def this(id: TnodeID, a: ActorRef) = {
    this(id.idVal, a)
  }
}

trait TActorRef {
  def actorref: ActorRef

  def getClient(selfid: idAddress): Transmitter = {
    new Transmitter(actorref, selfid)
  }
}


case class nodeID(bytes: Array[Byte]) extends TnodeID {
  override val idVal = bytes

  def this(str: String) = this(Base64.decode(str))
}

trait TnodeID {
  def idVal: Array[Byte] = new Array(20)(0.toByte)

  def getArray(): Array[Byte] = idVal.toArray

  def getBase64: String = Base64.encode(idVal.toArray)

  @Override
  override def hashCode(): Int = idVal.hashCode()

  @Override
  override def toString(): String = getBase64

  @Override
  override def equals(obj: Any) = {
    obj match {
      case that: TnodeID => this.getBase64.equals(that.getBase64)
      case _ => false
    }
  }

  def at(of: Int): Byte = idVal(of)

  def length = idVal.length

  def <->(x: TnodeID) = TnodeID.distance(this, x)

  def toBigInt = BigInt.apply(1, idVal)
}

object TnodeID {
  val CHORDSIZE = BigInt.apply(2).pow(160)

  /**
   * 二つのTnodeID間の距離を返します。順序は関係しません。0から2&sup(){160}-1までの円形の空間上の劣弧の距離を返します。
   * @param id1 一つ目のnodeID。
   * @param id2 二つ目のnodeID。
   * @return TnodeID間の距離。
   */
  def distance(id1: TnodeID, id2: TnodeID): BigInt = {
    val x: BigInt = BigInt.apply(1, id1.getArray())
    val y: BigInt = BigInt.apply(1, id2.getArray())
    val HALF: BigInt = BigInt.apply(2).pow(159)
    val x_y_subtract: BigInt = (x - y).abs; // abs(x-y)
    x_y_subtract >= HALF match {
      case true => x_y_subtract - HALF
      case false => x_y_subtract
    }
  }

  /**
   * Chordアルゴリズム上の円形空間で、alphaとomegaの間にXを置けるかを判定します。
   * @param X 評価の対象となるTnodeID。
   * @param alpha 区間の始点。
   * @param omega 区間の終点。
   * @return
   */
  def belongs(X: TnodeID, alpha: TnodeID, omega: TnodeID): Boolean = {
    val big_X: BigInt = X.toBigInt
    val big_alpha: BigInt = alpha.toBigInt
    val big_omega: BigInt = omega.toBigInt

    big_alpha.compare(big_omega) match {
      case 0 => true
      case _ =>
        big_omega.compare(big_alpha) match {
          case x if x < 0 =>
            val Left: Boolean = (big_alpha.compare(big_X) < 0) ∧ (big_X.compare(CHORDSIZE) < 0)
            val Right: Boolean = (BigInt(0).compare(big_X) < 0) ∧ (big_omega.compare(big_X) >= 0)
            Left ∨ Right
          case x if 0 < x => (big_alpha.compare(big_X) < 0) ∧ (big_X.compare(big_omega) <= 0)
        }
    }
  }

  def newNodeId: nodeID = {
    val arr = Array.fill[Byte](20)(0)
    Random.nextBytes(arr)
    new nodeID(arr)
  }

}
