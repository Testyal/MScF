import billy.mscf.Msf
import billy.syntax.msf._
import scalaz.{ Arrow, INil, Maybe, MaybeT, Show }
import scalaz.effect.IO

object Main extends SafeApp {
  def putStrLn(x: Any): IO[Unit] = IO {
    println(x)
  }

  type ~>[In, Out] = Msf[IO, In, Out]
  type ~>?[In, Out] = Msf[({type MaybeTIO[A] = MaybeT[IO, A]})#MaybeTIO, In, Out]

  def printingSf[S: Show]: S ~> Unit = Msf.arr[IO, S, String](implicitly[Show[S]].shows) >>> Msf.liftS(putStrLn)

  def capitalizingSfWithDots: (String, Int) ~> (String, Int) = Msf { case (input, dotCount) =>
    val capitalizedWithDots = input.toUpperCase ++ ".".repeat(dotCount)

    IO {
      ((capitalizedWithDots, dotCount + 5), capitalizingSfWithDots)
    }
  }

  def squaringAndPrintingMsf: Double ~> Double = Msf { x =>
    val squared = x * x
    for {
      _ <- putStrLn(s"$x squared is $squared")
    } yield {
      (squared, squaringAndPrintingMsf)
    }
  }

  def squaringMsf: Double ~> Double = Msf.arr { x => x * x }

  def printingMsf: Double ~> Double = Msf.liftS { x => putStrLn(s"Something squared is $x").map(_ => x) }

  def squaringAndPrintingMsf2: Double ~> Double = Msf.sequence(squaringMsf, printingMsf)

  def enhancedPrintingMsf: (Double, Double) ~> Unit = Msf.liftS
  { case (x, squared) => putStrLn(s"$x squared is $squared") }

  def squaringAndPrintingMsf3: Double ~> Unit = {
    (Msf.arr[IO, Double, Double](identity: Double => Double) &&& squaringMsf) >>>
    Msf.arr { case (x, squared) => ((x, squared), squared) } >>>
    Msf.first(enhancedPrintingMsf) >>>
    Msf.arr { case (_, squared) => squared }
  }

  def squaringAndPrintingMsf4: Double ~> Unit = {
    (squaringMsf                                              >>=
    Msf.arr { case (x, squared) => ((x, squared), squared) }) >>>
    Msf.first(enhancedPrintingMsf)                            >>>
    Msf.arr { case (_, squared) => squared }
  }

  def run(args: Array[String]): IO[Unit] = {
    for {
      _ <- squaringAndPrintingMsf4.step(4)
    } yield {
      ()
    }
  }
}
