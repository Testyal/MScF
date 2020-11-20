package billy.syntax

import billy.mscf.Msf
import scalaz.{ICons, IList, INil, Monad}

object msf {
  implicit final class MsfOps[M[_] : Monad, In, Out](private val msf: Msf[M, In, Out]) {
    private val M: Monad[M] = implicitly[Monad[M]]
    import M.monadSyntax._

    def step(a: In): M[(Out, Msf[M, In, Out])] = Msf.step(a, msf)
    def head(a: In): M[Out] = Msf.head(a, msf)
    def tail(a: In): M[Msf[M, In, Out]] = Msf.tail(a, msf)

    def first[Other]: Msf[M, (In, Other), (Out, Other)] = Msf.first(msf)
    def second[Other]: Msf[M, (Other, In), (Other, Out)] = Msf.second(msf)

    def andThen[FarOut](msf2: Msf[M, Out, FarOut]): Msf[M, In, FarOut] = Msf.compose(msf, msf2)
    def >>>[FarOut](msf2: Msf[M, Out, FarOut]): Msf[M, In, FarOut] = andThen(msf2)
    // IntelliJ is freaking out about the perfectly compilable '>>>' in this line
    def ***[In2, Out2](msf2: Msf[M, In2, Out2]): Msf[M, (In, In2), (Out, Out2)] = msf.first[In2] >>> msf2.second[Out]
    def &&&[Out2](msf2: Msf[M, In, Out2]): Msf[M, In, (Out, Out2)] = Msf.arr((a: In) => (a, a)) >>> (msf *** msf2)

    def embed(inList: IList[In]): M[IList[Out]] = inList match {
      case INil()            => M.pure(INil())
      case ICons(head, tail) => msf.step(head).flatMap { case (out, msf2) =>
        msf2.embed(tail).map { outs =>
          out :: outs
        }
      }
    }
  }

  implicit final class FeedbackMsfOp[M[_] : Monad, In, Out, State](private val msf: Msf[M, (In, State), (Out, State)]) {
    def feedback(state: State): Msf[M, In, Out] = Msf.feedback(state, msf)
  }
}
