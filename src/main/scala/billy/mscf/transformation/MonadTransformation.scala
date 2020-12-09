package billy.mscf.transformation

import billy.mscf.Msf
import billy.mscf.syntax.msf._

import scalaz.{ Monad, ~> }

object MonadTransformation {
  /**
   * Transforms the monadic context of an MSF according to a natural transformation.
   * <p>
   * An MSF may be provided in a particular context, like the "do-nothing" context [[scalaz.Id.Id]], and you need to
   * compose this MSF with one acting in another context, like [[scalaz.effect.IO]]. If there is a way to push values
   * from the former context into the latter via a natural transformation, this `transform` function transforms the
   * context of the former MSF into that of the latter MSF, allowing them to be composed.
   *
   * @example Consider a "pure" MSF `squaringMsf` with type `Msf[Id.Id, Int, Int]` which squares a number, and an
   *          "impure" MSF `printingMsf` with type `Msf[IO, Int, Unit]` which prints a number. We would like to compose
   *          these two MSFs to print the square of a number, but since the monadic contexts are different, this is
   *          currently impossible. However, there is a simple natural transformation `idToIo` between `Id` and `IO`
   *          given by `x => IO { x }`. Therefore, by using transform, we can push `squaringMsf` into the IO context
   *          and compose:
   * {{{
   * val ioSquaringMsf: Msf[IO, Int, Int] = Msf.transform(squaringMsf)(idToIo)
   * val squareAndPrint: Msf[IO, Int, Unit] = ioSquaringMsf >>> printingMsf
   *
   * squareAndPrint.head(5) // prints "25"
   * }}}
   *
   * @param msf            the MSF to transform.
   * @param monadTransform the natural transformation to transform the MSF with.
   * @tparam InM  the monadic context of the given MSF.
   * @tparam OutM the output monadic context of the given natural transformation.
   * @tparam In   the input type of the given MSF.
   * @tparam Out  the output type of the given MSF.
   *
   * @return an MSF with monadic context `OutM`.
   */
  def transform[InM[_]: Monad, OutM[_]: Monad, In, Out](msf: Msf[InM, In, Out])
                                                       (monadTransform: InM ~> OutM): Msf[OutM, In, Out] = Msf { in =>
    val OutM: Monad[OutM] = implicitly[Monad[OutM]]
    import OutM.monadSyntax._

    for {
      outAndMsf <- monadTransform(msf.step(in))
      (out, newMsf) = outAndMsf
    } yield {
      (out, transform(newMsf)(monadTransform))
    }
  }
}
