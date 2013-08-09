package momijikawa.p2pscalaproto

/**
 * Created with IntelliJ IDEA.
 * User: qwilas
 * Date: 13/07/18
 * Time: 2:16
 * To change this template use File | Settings | File Templates.
 */

import scalaz._
import Scalaz._
import scalaz.Ordering.GT
import Utility.Utility.pass


trait stabilizationStrategy {
  type stabilizeState = State[ChordState, stabilizationStrategy]
  type stabilized = (ChordState, stabilizationStrategy)
  val doStrategy: stabilizeState

  def apply(st: ChordState): stabilized = doStrategy.run(st)
}

object stabilizationSuccDeadStrategy extends stabilizationStrategy {
  // Succが死んでいるのでなんとかする。


  val recoverSuccList: State[ChordState, ChordState] = {
    for {
      st <- get[ChordState]
      cleared <- put(st.copy(succList = st.succList.reverse.tail.reverse))
      st2 <- get
      //joined <- ChordState.join(st2.succList.reverse.head, st.stabilizer)(st2)
      joined <- ChordState.join(st2.succList.reverse.head, st.stabilizer)
      ret <- get[ChordState]
    } yield ret
  }

  val joinPred: State[ChordState, ChordState] = {
    //　簡潔に@リファクタ@する
    val stopper = (successed: Boolean) => State[ChordState, ChordState] {
      (st: ChordState) =>
        successed match {
          case true => (st, st)
          case false =>
            // ついに全接続が継続できなくなった
            System.out.println("全接続経路が破綻しました。停止します")
            (for {
              st <- get[ChordState]
              _ <- pass(st.stabilizer ! StopStabilize)
              _ <- put(st.copy(succList = List[idAddress](st.selfID.get), pred = None))
              rslt <- get[ChordState]
            } yield rslt).run(st)
        }
    }

    val connectPredAndJoin2: State[ChordState, ChordState] = State[ChordState, ChordState] {
      (cs: ChordState) =>
        cs.pred match {
          case Some(pred) =>
            (for {
              joining <- ChordState.join(pred, cs.stabilizer)
              result <- stopper(joining)
            } yield result).run(cs)
          case None =>
            (cs, cs)
        }
    }

    /*val connectPredandJoin: State[ChordState, ChordState] = // s => Option(s,a) or Option(s => s,a)
    for {
        cs <- get[ChordState]
        pred <- cs.pred
        joining <- ChordState.join(pred, cs.stabilizer)
        joinedState <- get[ChordState]
        result <- stopper(joining)
        //joined <- get[ChordState] // lift
      } yield result*/

    connectPredAndJoin2
  }

  val doStrategy = State[ChordState, stabilizationStrategy] {
    cs: ChordState =>
      System.out.println("Successorに接続できません")
      System.out.println("Successorを破棄")
      cs.succList.size ?|? 1 match {
        case GT => // succListに余裕あり
          recoverSuccList.run(cs) match {
            case (st: ChordState, _) => (st, stabilizationSuccDeadStrategy)
          }
        case _ =>
          joinPred.run(cs) match {
            case (st: ChordState, _) => (st, stabilizationSuccDeadStrategy)
          }
      }
  }
}

object stabilizationPreSuccDeadStrategy extends stabilizationStrategy {
  val doStrategy = State[ChordState, stabilizationStrategy] {
    cs: ChordState =>
      cs.succList.last.getClient(cs.selfID.get).amIPredecessor()
      (cs, stabilizationPreSuccDeadStrategy)
  }
}

object stabilizationRightStrategy extends stabilizationStrategy {
  val doStrategy = State[ChordState, stabilizationStrategy] {
    cs: ChordState =>
      System.out.println("Right Strategy")
      cs.succList.last.getClient(cs.selfID.get).amIPredecessor()
      (cs, stabilizationRightStrategy)
  }
}

object stabilizationGaucheStrategy extends stabilizationStrategy {
  val doStrategy = State[ChordState, stabilizationStrategy] {
    (cs: ChordState) =>

      val preSucc = cs.succList.last.getClient(cs.selfID.get).yourPredecessor
      preSucc match {
        case Some(v) =>
          System.out.println("Gauche Strategy: New Successor is: " + v.getBase64)
          //rewrite self.

          val renewedcs: State[ChordState, ChordState] = for {
            _ <- modify[ChordState](_.copy(succList = List[idAddress](v)))
            newcs <- get[ChordState]
            _ <- pass(newcs.succList.last.getClient(newcs.selfID.get).amIPredecessor())
          } yield newcs


          renewedcs.run(cs) match {
            case (newst: ChordState, _) => (newst, stabilizationGaucheStrategy)
          }
        case None =>
          stabilizationSuccDeadStrategy.doStrategy(cs)
      }
  }
}

object stabilizationOKStrategy extends stabilizationStrategy {
  val doStrategy: stabilizeState =
    State[ChordState, stabilizationStrategy] {
      (cs: ChordState) =>
        println("It's all right")
        (cs, stabilizationOKStrategy)
    }
}
