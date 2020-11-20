import billy.mscf.Msf
import billy.syntax.mscf._
import scalaz.{ICons, INil, Show}
import scalaz.effect.IO

object Main extends SafeApp {
  def putStrLn(x: Any): IO[Unit] = IO { println(x) }

  def printingSf[S: Show]: Msf[IO, S, Unit] = Msf.arr[IO, S, String](implicitly[Show[S]].shows) >>> Msf.liftS(putStrLn)
  def capitalizingSfWithDots: Msf[IO, (String, Int), (String, Int)] = Msf { case (input, dotCount) =>
    val capitalizedWithDots = input.toUpperCase ++ ".".repeat(dotCount)

    IO { ((capitalizedWithDots, dotCount + 5), capitalizingSfWithDots) }
  }

  def run(args: Array[String]): IO[Unit] = {
    val feedbackedMsf = capitalizingSfWithDots.feedback(5)

    implicit def showFromToString: Show[String] = Show.showFromToString[String]
    for {
      _ <- (feedbackedMsf >>> printingSf[String]).embed("Hello" :: "World" :: "Penis" :: INil())
    } yield ()
  }
}
