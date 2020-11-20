import scalaz.effect.IO

abstract class SafeApp {
  def run(args: Array[String]): IO[Unit]

  def main(args: Array[String]): Unit = {
    run(args).unsafePerformIO()
  }
}
