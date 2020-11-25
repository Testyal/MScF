package billy.mscf.instance

import billy.mscf.Msf
import scalaz.{ Arrow, Monad }

object MsfInstances {
  implicit def msfArrow[M[_]: Monad]: Arrow[({
    type T[A, B] = Msf[M, A, B]
  })#T] = new Arrow[({type T[A, B] = Msf[M, A, B]})#T] {
    override def arr[In, Out](f: In => Out): Msf[M, In, Out] = Msf.arr(f)

    override def id[A]: Msf[M, A, A] = Msf.arr(identity)

    override def first[In, Out, Other](fa: Msf[M, In, Out]): Msf[M, (In, Other), (Out, Other)] = Msf.first(fa)

    override def compose[In, Mid, Out](f: Msf[M, Mid, Out], g: Msf[M, In, Mid]): Msf[M, In, Out] = Msf.sequence(g, f)
  }
}
