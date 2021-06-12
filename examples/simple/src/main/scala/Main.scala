import zio._
import zio.console._
import io.github.redroy44.zioquartz.core._

import zio.duration._

object Main extends App {
  val program: ZIO[ZEnv with Has[QuartzScheduler], Throwable, Unit] = for {
    q   <- Queue.bounded[String](10)
    _   <- QuartzScheduler.createJobSchedule("test_schedule", q, "test_message", "0/3 * * ? * * *")
    _   <- ZIO.sleep(3.seconds)
    rec <- q.takeAll
    _   <- putStrLn(rec.toString)
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program.provideCustomLayer(QuartzSchedulerLive.layer).exitCode
}
