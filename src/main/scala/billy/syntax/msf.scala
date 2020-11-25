package billy.syntax

import billy.mscf.Msf
import scalaz.{ Arrow, ICons, IList, INil, Monad }

object msf {

  implicit final class MsfOps[M[_]: Monad, In, Out](private val msf: Msf[M, In, Out]) {
    private val M: Monad[M] = implicitly[Monad[M]]

    def step(a: In): M[(Out, Msf[M, In, Out])] = Msf.step(msf)(a)

    def head(a: In): M[Out] = Msf.head(msf)(a)

    def tail(a: In): M[Msf[M, In, Out]] = Msf.tail(msf)(a)

    def first[Other]: Msf[M, (In, Other), (Out, Other)] = Msf.first(msf)

    def second[Other]: Msf[M, (Other, In), (Other, Out)] = Msf.second(msf)

    def andThen[FarOut](msf2: Msf[M, Out, FarOut]): Msf[M, In, FarOut] = Msf.sequence(msf, msf2)

    def >>>[FarOut](msf2: Msf[M, Out, FarOut]): Msf[M, In, FarOut] = Msf.sequence(msf, msf2)

    def ***[In2, Out2](msf2: Msf[M, In2, Out2]): Msf[M, (In, In2), (Out, Out2)] = Msf.parallel(msf, msf2)

    def &&&[Out2](msf2: Msf[M, In, Out2]): Msf[M, In, (Out, Out2)] = Msf.broadcast(msf, msf2)

    def bind[FarOut](msf2: Msf[M, (In, Out), FarOut]): Msf[M, In, FarOut] = Msf.bind(msf, msf2)

    def >>=[FarOut](msf2: Msf[M, (In, Out), FarOut]): Msf[M, In, FarOut] = Msf.bind(msf, msf2)

    def embed(inList: IList[In]): M[IList[Out]] = Msf.embed(inList, msf)
  }

  implicit final class FeedbackMsfOp[M[_]: Monad, In, Out, State](private val msf: Msf[M, (In, State), (Out, State)]) {
    def feedback(state: State): Msf[M, In, Out] = Msf.feedback(state, msf)
  }

  implicit final class ReactimateMsfOp[M[_]: Monad](private val msf: Msf[M, Unit, Unit]) {
    def reactimate: M[Unit] = Msf.reactimate(msf)
  }

}
