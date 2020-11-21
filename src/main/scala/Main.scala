import billy.mscf.Msf
import billy.syntax.msf._
import scalaz.{INil, Show}
import scalaz.effect.IO

object Main extends SafeApp {
  def putStrLn(x: Any): IO[Unit] = IO { println(x) }

  type ~>[In, Out] = Msf[IO, In, Out]

  def printingSf[S: Show]: S ~> Unit = Msf.arr[IO, S, String](implicitly[Show[S]].shows) >>> Msf.liftS(putStrLn)
  def capitalizingSfWithDots: (String, Int) ~> (String, Int) = Msf { case (input, dotCount) =>
    val capitalizedWithDots = input.toUpperCase ++ ".".repeat(dotCount)

    IO { ((capitalizedWithDots, dotCount + 5), capitalizingSfWithDots) }
  }

  def run(args: Array[String]): IO[Unit] = {
    val feedbackedMsf = capitalizingSfWithDots.feedback(5)

    implicit def showFromToString: Show[String] = Show.showFromToString[String]
    for {
      _ <- (feedbackedMsf >>> printingSf[String]).embed("Hello" :: "World" :: INil())
    } yield ()
  }
}
