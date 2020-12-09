package billy.mscf.syntax

import billy.mscf.Msf
import billy.mscf.combinator.ReaderTCombinator

import scalaz.{ Monad, ReaderT }

object combinator {

  implicit final class ReaderOps[M[_]: Monad, Env, In, Out](msf: Msf[ReaderT[Env, M, *], In, Out]) {
    /**
     * [[billy.mscf.combinator.ReaderTCombinator#runReaderS]]
     */
    def runReaderS(environment: Env): Msf[M, In, Out] = ReaderTCombinator.runReaderS(msf, environment)
  }

}
