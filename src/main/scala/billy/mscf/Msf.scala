package billy.mscf

import scalaz.{ Arrow, ICons, IList, INil, Monad }
import scalaz.syntax.monad._

/**
 * A ''monadic stream function'' (MSF) is an abstraction to represent synchronous, effectful, and causal functions
 * which may change over time.
 *
 * == Creating MSFs ==
 * An MSF is parameterized by the behavior of [[billy.mscf.Msf#step]], which may be specified directly by using
 * [[billy.mscf.Msf#apply]] to construct a new MSF. Alternatively, there are the methods [[billy.mscf.Msf#arr]]
 * and [[billy.mscf.Msf#liftS]] to lift pure functions and monadic computations into MSFs, and a number of methods
 * to compose MSFs together. Most important of these are [[billy.mscf.Msf#sequence]], [[billy.mscf.Msf#parallel]],
 * and [[billy.mscf.Msf#broadcast]], which are commonly used alongside the input/output-adding methods
 * [[billy.mscf.Msf#first]] and [[billy.mscf.Msf#second]].
 *
 * For example, we can create an MSF running in the IO context which squares a number, prints it, and returns the
 * square, leaving the MSF unchanged. To do this directly,
 * {{{
 * def putStrLn(x: Any) = IO { println(x) }
 * val squaringAndPrintingMsf: Msf[IO, Double, Double] = Msf { x =>
 *   val squared = x * x
 *   for {
 *     _ <- putStrLn(s"$x squared is $squared")
 *   } yield (squared, squaringAndPrintingMsf)
 * }
 * }}}
 * Alternatively, we can create two MSFs, one doubling and one printing, then sequencing them together.
 * {{{
 * val squaringMsf: Msf[IO, Double, Double] = Msf.arr { x => x * x }
 * val printingMsf: Msf[IO, Double, Double] = Msf.liftS { x => putStrLn(s"Something squared is $x").map(_ => x) }
 * val squaringAndPrintingMsf: Msf[IO, Double, Double] = Msf.sequence(squaringMsf, printingMsf)
 * }}}
 * The obvious issue with composing them naively is that we cannot reference the input to the squaring MSF inside the
 * printing one. It is possible to resolve this issue by using the other composition functions available to us in
 * clever ways:
 * {{{
 * val enhancedPrintingMsf: Msf[IO, (Double, Double), Unit] = { case (x, squared) => putStrLn(s"$x squared is
 * $squared") }
 * val squaringAndPrintingMsf: Msf[IO, Double, Double] =
 *   (Msf.arr[IO, Double, Double](identity) &&& squaringMsf)  >>>
 *   Msf.arr { case (x, squared) => ((x, squared), squared) } >>>
 *   Msf.first(enhancedPrintingMsf)                           >>>
 *   Msf.arr { case (_, squared) => squared }
 * }}}
 * Here, `>>>` and `&&&` are operator shorthand for `sequence` and `broadcast` respectively, defined in
 * [[billy.syntax.msf]]. The benefit of this approach is in the explicitness of the inputs and outputs in our new
 * printing MSF, making it reusable in other MSFs. The rest is just plumbing.
 *
 * == Evaluating MSFs ==
 * An MSF is typically hooked to a stream of input data. To step forward an MSF with sample inputs, use the
 * [[billy.mscf.Msf#step]] method, which returns an output and continuation of the MSF in the monadic context. There
 * are also methods [[billy.mscf.Msf#head]] and [[billy.mscf.Msf#tail]], which do the same thing but only return the
 * output or continuation respectively.
 *
 * An list of sample inputs can be provided to an MSF with the method [[billy.mscf.Msf#embed]], and a unit-carrying
 * MSF (i.e. one of type `Msf[M, Unit, Unit]`) can be simulated using [[billy.mscf.Msf#reactimate]].
 *
 * == Syntax Extensions ==
 * The object [[billy.syntax.msf]] contains a number of convenient extensions to MSF objects to avoid having to call
 * methods in [[billy.mscf.Msf]].
 *
 * @tparam M   the monadic context under which an output stream and continuation of the MSF is returned.
 * @tparam In  the type of the input data to the MSF.
 * @tparam Out the type of the output data to the MSF.
 *
 * @since 0.1
 */
final case class Msf[M[_]: Monad, In, Out] private(private val runMsf: In => M[(Out, Msf[M, In, Out])])

object Msf {
  def step[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[(Out, Msf[M, In, Out])] = msf.runMsf(a)
  def head[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[Out] = msf.runMsf(a).map(_._1)
  def tail[M[_] : Monad, In, Out](a: In, msf: Msf[M, In, Out]): M[Msf[M, In, Out]] = msf.runMsf(a).map(_._2)

  def tail[M[_]: Monad, In, Out](msf: Msf[M, In, Out])(a: In): M[Msf[M, In, Out]] = msf.runMsf(a).map(_._2)

  def first[M[_]: Monad, In, Out, Other](msf: Msf[M, In, Out]): Msf[M, (In, Other), (Out, Other)] = {
    Msf { case (in, other) =>
      for {
        outAndMsf <- step(msf)(in)
        (out, msf2) = outAndMsf
      } yield {
        ((out, other), first(msf2))
      }
    }
  }

  def second[M[_]: Monad, Other, In, Out](msf: Msf[M, In, Out]): Msf[M, (Other, In), (Other, Out)] = {
    def swap[A, B](xy: (A, B)): (B, A) = xy.swap

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
      unitAndMsf <- step(msf)(())
      (_, msf2) = unitAndMsf
      _ <- reactimate(msf2)
    } yield {
      ()
    }
  }

}
