package io.github.redroy44.zioquartz.core

import zio._
import zio.clock._
import zio.console._
import zio.duration._
import zio.test.Assertion._
import zio.test._

object QuartzSchedulerFunctionalSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] = suite("QuartzSchedulerFunctionalSpec")(
    testM("test") {
      val result = for {
        q   <- Queue.bounded[String](10)
        _   <- QuartzScheduler.createSchedule(
                 name = "test",
                 cronExpression = "0/3 * * ? * * *"
               )
        s   <- QuartzScheduler.getSchedules
        _   <- putStrLn(s"$s")
        _   <- QuartzScheduler.schedule("test", q, "test_message")
        _   <- ZIO.sleep(2.seconds)
        rec <- q.takeAll
      } yield assert(rec)(equalTo(List("test_message")))
      result.provideLayer(Console.live ++ Clock.live ++ QuartzSchedulerLive.layer)
    }
  )
}
