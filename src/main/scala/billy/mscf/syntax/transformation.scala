package billy.mscf.syntax

import billy.mscf.Msf
import billy.mscf.transformation.MonadTransformation
import scalaz.{ Monad, ~> }

object transformation {
  implicit class MonadTransformationOps[InM[_]: Monad, In, Out](msf: Msf[InM, In, Out]) {
    /**
     * [[billy.mscf.transformation.MonadTransformation#transform]]
     */
    def transform[OutM[_]: Monad](monadTransform: InM ~> OutM): Msf[OutM, In, Out] = {
      MonadTransformation.transform(msf)(monadTransform)
    }
  }
}
