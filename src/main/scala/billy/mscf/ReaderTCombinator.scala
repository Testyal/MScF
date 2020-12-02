package billy.mscf

import scalaz.{ Monad, ReaderT }
import billy.mscf.syntax.msf._

object ReaderTCombinator {

  def runReaderS[M[_]: Monad, Env, In, Out](msf: Msf[ReaderT[Env, M, *], In, Out], env: Env): Msf[M, In, Out] = {
    Msf { in =>
      val runMsf = for {
        outAndMsf <- msf.step(in)
        (out, newMsf) = outAndMsf
      } yield {
        (out, runReaderS(newMsf, env))
      }
      runMsf.run(env)
    }
  }

}
