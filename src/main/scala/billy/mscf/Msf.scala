package billy.mscf

import scalaz.{ Arrow, ICons, IList, INil, Monad }
import scalaz.syntax.monad._

final case class Msf[M[_] : Monad, In, Out](private val runMsf: In => M[(Out, Msf[M, In, Out])])

object Msf {
  def step[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[(Out, Msf[M, In, Out])] = msf.runMsf(a)
  def head[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[Out] = msf.runMsf(a).map(_._1)
  def tail[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[Msf[M, In, Out]] = msf.runMsf(a).map(_._2)

  def tail[M[_]: Monad, In, Out](msf: Msf[M, In, Out])(a: In): M[Msf[M, In, Out]] = msf.runMsf(a).map(_._2)

  def first[M[_]: Monad, In, Out, Other](msf: Msf[M, In, Out]): Msf[M, (In, Other), (Out, Other)] = {
    Msf { case (in, other) =>
      for {
        outAndMsf   <- step(msf)(in)
        (out, msf2) = outAndMsf
      } yield {
        ((out, other), first(msf2))
      }
    }
  }

  def second[M[_]: Monad, Other, In, Out](msf: Msf[M, In, Out]): Msf[M, (Other, In), (Other, Out)] = {
    def swap[A, B](xy: (A, B)): (B, A) = {
      val (x, y) = xy
      (y, x)
    }

    sequence(sequence(arr(swap[Other, In]), first[M, In, Out, Other](msf)), arr(swap[Out, Other]))
  }

  def sequence[M[_]: Monad, In, Mid, Out](msf1: Msf[M, In, Mid], msf2: Msf[M, Mid, Out]): Msf[M, In, Out] = Msf { in =>
    for {
      midAndMsf <- step(msf1)(in)
      (mid, msf1Prime) = midAndMsf
      outAndMsf <- step(msf2)(mid)
      (out, msf2Prime) = outAndMsf
    } yield {
      (out, sequence(msf1Prime, msf2Prime))
    }
  }

  def parallel[M[_]: Monad, In1, In2, Out1, Out2](msf1: Msf[M, In1, Out1],
                                                  msf2: Msf[M, In2, Out2]): Msf[M, (In1, In2), (Out1, Out2)] = {
    sequence(first[M, In1, Out1, In2](msf1), second[M, Out1, In2, Out2](msf2))
  }

  def broadcast[M[_]: Monad, In, Out1, Out2](msf1: Msf[M, In, Out1],
                                             msf2: Msf[M, In, Out2]): Msf[M, In, (Out1, Out2)] = {
    sequence(arr { x => (x, x) }, parallel(msf1, msf2))
  }

  def bind[M[_]: Monad, In, Mid, Out](msf1: Msf[M, In, Mid], msf2: Msf[M, (In, Mid), Out]): Msf[M, In, Out] = {
    sequence(broadcast(arr(identity), msf1), msf2)
  }

  def arr[M[_]: Monad, In, Out](f: In => Out): Msf[M, In, Out] = Msf { a =>
    implicitly[Monad[M]].pure((f(a), arr(f)))
  }

  def liftS[M[_]: Monad, In, Out](f: In => M[Out]): Msf[M, In, Out] = Msf { a =>
    f(a).map((_, liftS(f)))
  }

  def feedback[M[_] : Monad, In, Out, State](state: State, msf: Msf[M, (In, State), (Out, State)]): Msf[M, In, Out] = Msf { a =>
    for {
      bsm                     <- msf.runMsf(a, state)
      ((b, newState), newMsf) = bsm
    } yield (b, feedback(newState, newMsf))
  }

  def embed[M[_]: Monad, In, Out](values: IList[In], msf: Msf[M, In, Out]): M[IList[Out]] = {
    val M = implicitly[Monad[M]]
    import M.monadSyntax._

    values match {
      case INil()            => M.pure(INil())
      case ICons(head, tail) => {
        for {
          outAndMsf <- step(msf)(head)
          (out, msf2) = outAndMsf
          outs <- embed(tail, msf2)
        } yield {
          out :: outs
        }
      }
    }
  }

  def reactimate[M[_]: Monad](msf: Msf[M, Unit, Unit]): M[Unit] = {
    for {
      unitAndMsf <- step(msf)()
      (_, msf2) = unitAndMsf
    } yield {
      reactimate(msf2)
    }
  }

}
