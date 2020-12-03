package billy.mscf.combinator

import billy.mscf.Msf
import billy.mscf.syntax.msf._

import scalaz.{ Monad, ReaderT }

object ReaderTCombinator {

  /**
   * Provides an environment to an MSF whose monadic context is a [[scalaz.ReaderT]].
   * <p>
   * An MSF may depend on some externally provided configuration unavailable at compile time. The `ReaderT` monad
   * transformer exists to resolve this problem. In order to provide the configuration at runtime, and therefore
   * remove the reader from the monadic context, this function is provided.
   * <p>
   * This function is the MSF equivalent of [[scalaz.Kleisli#run]].
   *
   * @example Consider a program which gets a number `n` at runtime, and an MSF `msf` which adds a configurable
   *          number of periods to an input string.
   * {{{
   * val msf: Msf[ReaderT[Int, IO, *], String, String] = Msf { in =>
   *   ReaderT { env =>
   *     IO { in ++ '.'.repeat(env) }
   *   }
   * }
   *
   * val periodAmount = 5
   * val configuredMsf = runReaderS(msf, periodAmount)
   *
   * configuredMsf.step("Hello world") // = "Hello world....."
   * }}}
   *
   * @param msf         the MSF with a `ReaderT` monadic context to provide a configuration for.
   * @param environment the configuration to provide to the `ReaderT` context.
   * @tparam M   the monad transformed by `ReaderT`.
   * @tparam Env the type of configuration for the `ReaderT`.
   * @tparam In  the input type of the MSF.
   * @tparam Out the output type of the MSF.
   *
   * @return an MSF whose monadic context is `M` resulting from providing a configuration to the given MSF.
   */
  def runReaderS[M[_]: Monad, Env, In, Out](msf: Msf[ReaderT[Env, M, *], In, Out],
                                            environment: Env): Msf[M, In, Out] = Msf { in =>
    val runMsf = for {
      outAndMsf <- msf.step(in)
      (out, newMsf) = outAndMsf
    } yield {
      (out, runReaderS(newMsf, environment))
    }
    runMsf.run(environment)
  }

}
