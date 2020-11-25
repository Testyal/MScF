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
  /**
   * Steps a monadic stream function forward with a given sample input.
   *
   * @example An MSF which capitalizes its input until it encounters the input "stop" and lies in a safely ignorable
   *          monadic context `M` may be stepped like so
   * {{{
   * scala> val (out, msf2) = capitalizingMsf.step("Hello world")
   * val out: String = "HELLO WORLD"
   * val msf2: Msf[M, String, String] = [...]
   *
   * scala> val (out2, msf3) = msf2.step("stop")
   * val out2: String = "stop"
   * val msf3: Msf[M, String, String] = [...]
   *
   * scala> val (out3, _) = msf3.step("Hello again, world")
   * val out3: String = "Hello again, world"
   * }}}
   * @see [[billy.mscf.Msf#head]], [[billy.mscf.Msf#tail]]
   *
   * @param a   the value to pass into the MSF.
   * @param msf the msf to step forward.
   * @tparam M   the monadic context under which the output and continuation are returned.
   * @tparam In  the msf's input type.
   * @tparam Out the msf's output type.
   *
   * @return the output of the MSF's computation and a continuation of the MSF under the monadic context `M`.
   */
  def step[M[_]: Monad, In, Out](msf: Msf[M, In, Out])(a: In): M[(Out, Msf[M, In, Out])] = msf.runMsf(a)

  /**
   * Steps a monadic stream function forward with a given sample input, returning only the output.
   *
   * @see [[billy.mscf.Msf#step]], [[billy.mscf.Msf#tail]]
   *
   * @param msf the MSF to step forward.
   * @param a   the sample to pass into the MSF.
   * @tparam M   the monadic context under which the output is returned.
   * @tparam In  the type of the input to the MSF.
   * @tparam Out the type of the output from the MSF.
   *
   * @return the output of the MSF's computation under the monadic context `M`.
   */
  def head[M[_]: Monad, In, Out](msf: Msf[M, In, Out])(a: In): M[Out] = msf.runMsf(a).map(_._1)

  /**
   * Steps a monadic stream function forward with a given sample input, returning only the continuation of the MSF.
   *
   * @see [[billy.mscf.Msf#step]], [[billy.mscf.Msf#head]]
   *
   * @param msf the MSF to step forward.
   * @param a   the sample to pass into the MSF.
   * @tparam M   the monadic context under which the continuation is returned.
   * @tparam In  the type of the input to the MSF.
   * @tparam Out the type of the output from the MSF.
   *
   * @return the continuation of the MSF under the monadic context `M`.
   */
  def tail[M[_]: Monad, In, Out](msf: Msf[M, In, Out])(a: In): M[Msf[M, In, Out]] = msf.runMsf(a).map(_._2)

  /**
   * Extends an MSF with an additional input and output of the same type, which passes through the extended MSF with
   * nothing done to it.
   * <p>
   * This method and its sister [[billy.mscf.Msf#second]] are used for plumbing. The extended MSF will have new input
   * type `(In, Other)` and new output type `(Out, Other)`, where `In` and `Out` are the input and output types of
   * the original MSF.
   *
   * @example An MSF which takes in a double input and a string input, doubles the number, then prints the string and
   *          doubled number together can be implemented using `first` like so
   * {{{
   * val doublingPrintingMsf: Msf[IO, (Double, String), Unit] =
   *   Msf.sequence(Msf.first(Msf.arr { x => x + x }),
   *                Msf.liftS { case (str, doubled) =>
   *                  IO { println(s"Hello $str, your number doubled is $doubled." }
   *                })
   * }}}
   * @see [[billy.mscf.Msf#second]]
   *
   * @param msf the MSF to extend with an input and output of the same type.
   * @tparam M     the monadic context under which the MSF returns its output and continuation.
   * @tparam In    the input type of the MSF.
   * @tparam Out   the output type of the MSF.
   * @tparam Other the type of the new input and output to add to the MSF.
   *
   * @return the MSF extended with an additional input and output.
   */
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

  /**
   * Extends an MSF with an additional input and output of the same type, which passes through the extended MSF with
   * nothing done to it.
   * <p>
   * This method and its sister [[billy.mscf.Msf#first]] are used for plumbing. The extended MSF will have new input
   * type `(Other, In)` and new output type `(Other, Out)`, where `In` and `Out` are the input and output types of
   * the original MSF.
   *
   * @see [[billy.mscf.Msf#first]]
   *
   * @param msf the MSF to extend with an input and output of the same type.
   * @tparam M     the monadic context under which the MSF returns its output and continuation.
   * @tparam Other the type of the new input and output to add to the MSF.
   * @tparam In    the input type of the MSF.
   * @tparam Out   the output type of the MSF.
   *
   * @return the MSF extended with an additional input and output.
   */
  def second[M[_]: Monad, Other, In, Out](msf: Msf[M, In, Out]): Msf[M, (Other, In), (Other, Out)] = {
    def swap[A, B](xy: (A, B)): (B, A) = xy.swap

    sequence(sequence(arr(swap[Other, In]), first[M, In, Out, Other](msf)), arr(swap[Out, Other]))
  }

  /**
   * Joins two MSFs, attaching the output of the first to the input of the second.
   * <p>
   * The output of the joined MSF is equivalent to passing an input through the first MSF, taking the output of that,
   * then passing it as input to the second MSF. The continuation of the joined MSF after passing an input is
   * equivalent to the continuation of the the first MSF passed the input, joined with the continuation of the second
   * MSF passed the output of the first MSF.
   *
   * @example Consider two MSFs, one, called `capitalizingMsf` which capitalizes its input until it encounters the
   *          input "stop", and another, called `periodMsf` which adds a period. The joined MSF will capitalize its
   *          input and add a period, until it encounters the input "stop", from which time it will just add a period.
   *          We assume the monadic context `M` can be safely ignored.
   * {{{
   * scala> val capitalizingAndPeriodMsf = Msf.sequence(capitalizingMsf, periodMsf)
   * [...]
   *
   * scala> val (out, msf2) = capitalizingAndPeriodMsf.step("Hello world")
   * val out: String = "HELLO WORLD."
   * val msf2: Msf[M, String, String] = [...]
   *
   * scala> val (out2, msf3) = msf2.step("stop")
   * val out2: String = "stop."
   * val msf2: Msf[M, String, String] = [...]
   *
   * scala> val (out3, _) = msf3.step("Hello world")
   * val out3: String = "Hello world."
   * }}}
   *
   * @param msf1 the first MSF, whose output is joined to the input of the second.
   * @param msf2 the second MSF, whose input is joined to the output of the first.
   * @tparam M   the monadic context under which the output and continuation of the MSFs are returned.
   * @tparam In  the type of the input to the first MSF.
   * @tparam Mid the type of the output to the first MSF, and the type of the input to the second MSF.
   * @tparam Out the type of the output of the second MSF.
   *
   * @return the joined MSF.
   */
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

  /**
   * Joins two MSFs in parallel, creating an MSF with two inputs and two outputs.
   *
   * @param msf1
   * @param msf2
   * @tparam M
   * @tparam In1
   * @tparam In2
   * @tparam Out1
   * @tparam Out2
   *
   * @return
   */
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

  /**
   * Constructs a MSF from a pure function.
   * <p>
   * The output from the MSF given an input `x` is equal to `M.pure(f(x))`. The continuation is the same as the
   * original.
   *
   * @see [[billy.mscf.Msf#liftS]]
   *
   * @param f the pure function to create an MSF from.
   * @tparam M   the monadic context under which the MSF should return its output.
   * @tparam In  the input type of the function `f`, equivalently the input type to the created MSF.
   * @tparam Out the output type of the function `f`, equivalently the output type to the created MSF.
   *
   * @return an MSF created from the pure function `f`.
   */
  def arr[M[_]: Monad, In, Out](f: In => Out): Msf[M, In, Out] = Msf { a =>
    implicitly[Monad[M]].pure((f(a), arr(f)))
  }

  /**
   * Lifts a monadic computation into an MSF.
   * <p>
   * The output from the MSF given an input `x` is `f(x)`. The continuation is the same as the original.
   *
   * @see [[billy.mscf.Msf#arr]]
   *
   * @param f the monadic computation to lift into an MSF.
   * @tparam M   the monadic context under which `f` runs.
   * @tparam In  the input type of `f`.
   * @tparam Out the output type of `f`.
   *
   * @return an MSF lifted from the monadic computation `f`.
   */
  def liftS[M[_]: Monad, In, Out](f: In => M[Out]): Msf[M, In, Out] = Msf { a =>
    f(a).map((_, liftS(f)))
  }

  def feedback[M[_]: Monad, In, Out, State](state: State, msf: Msf[M, (In, State), (Out, State)]): Msf[M, In, Out] = {
    Msf { a =>
      for {
        bsm <- msf.runMsf(a, state)
        ((b, newState), newMsf) = bsm
      } yield {
        (b, feedback(newState, newMsf))
      }
    }
  }

  /**
   * Evaluates a MSF on a sequence of sample inputs.
   * <p>
   *
   *
   * @param values
   * @param msf
   * @tparam M
   * @tparam In
   * @tparam Out
   * @return
   */
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
