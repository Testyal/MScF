package billy.mscf

import scalaz.{Arrow, ICons, IList, INil, Monad}
import scalaz.syntax.monad._

final case class Msf[M[_] : Monad, In, Out](private val runMsf: In => M[(Out, Msf[M, In, Out])])

object Msf {
  def step[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[(Out, Msf[M, In, Out])] = msf.runMsf(a)
  def head[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[Out] = msf.runMsf(a).map(_._1)
  def tail[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[Msf[M, In, Out]] = msf.runMsf(a).map(_._2)

  def first[M[_] : Monad, A, B, C](msf: Msf[M, A, B]): Msf[M, (A, C), (B, C)] = Msf { ac =>
    for {
      b <- msf.runMsf(ac._1)
    } yield ((b._1, ac._2), first(msf))
  }
  def second[M[_] : Monad, A, B, C](msf: Msf[M, B, C]): Msf[M, (A, B), (A, C)] = Msf { ab =>
    for {
      c <- msf.runMsf(ab._2)
    } yield ((ab._1, c._1), second(msf))
  }

  def compose[M[_] : Monad, In, Mid, Out](msfa: Msf[M, In, Mid], msfb: Msf[M, Mid, Out]): Msf[M, In, Out] = Msf { a =>
    for {
      b <- head(a, msfa)
      c <- head(b, msfb)
    } yield (c, compose(msfa, msfb))
  }

  def arr[M[_] : Monad, In, Out](f: In => Out): Msf[M, In, Out] = Msf { a =>
    implicitly[Monad[M]].pure((f(a), arr(f)))
  }
  def liftS[M[_] : Monad, In, Out](f: In => M[Out]): Msf[M, In, Out] = Msf { a =>
    f(a).map((_, liftS(f)))
  }

  def feedback[M[_] : Monad, In, Out, State](state: State, msf: Msf[M, (In, State), (Out, State)]): Msf[M, In, Out] = Msf { a =>
    for {
      bsm                     <- msf.runMsf(a, state)
      ((b, newState), newMsf) = bsm
    } yield (b, feedback(newState, newMsf))
  }

}
