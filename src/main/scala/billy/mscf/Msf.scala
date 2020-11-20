package billy.mscf

import scalaz.{Arrow, ICons, IList, INil, Monad}
import scalaz.syntax.monad._

final case class Msf[M[_] : Monad, In, Out](private val runMsf: In => M[(Out, Msf[M, In, Out])])

object Msf {
  def step[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[(Out, Msf[M, In, Out])] = msf.runMsf(a)
  def head[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[Out] = msf.runMsf(a).map(_._1)
  def tail[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[Msf[M, In, Out]] = msf.runMsf(a).map(_._2)

  def first[M[_] : Monad, In, Out, Other](msf: Msf[M, In, Out]): Msf[M, (In, Other), (Out, Other)] = Msf { case (in, other) =>
    for {
      out <- msf.runMsf(in)
    } yield ((out, other), first(msf))
  }
  def second[M[_] : Monad, Other, In, Out](msf: Msf[M, In, Out]): Msf[M, (Other, In), (Other, Out)] = Msf { case (other, in) =>
    for {
      out <- msf.runMsf(in)
    } yield ((other, out), second(msf))
  }

  def compose[M[_] : Monad, In, Mid, Out](msf1: Msf[M, In, Mid], msf2: Msf[M, Mid, Out]): Msf[M, In, Out] = Msf { in =>
    for {
      midAndMsf        <- step(in, msf1)
      (mid, msf1Prime) = midAndMsf
      outAndMsf        <- step(mid, msf2)
      (out, msf2Prime) = outAndMsf
    } yield (out, compose(msf1Prime, msf2Prime))
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
