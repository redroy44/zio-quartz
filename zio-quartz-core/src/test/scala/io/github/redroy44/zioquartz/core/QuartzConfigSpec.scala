package io.github.redroy44.zioquartz.core

import zio.config._
import zio.test.Assertion._
import zio.test._

object QuartzConfigSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] = suite("QuartzConfigSpec")(
    testM("properly load default reference config from classpath") {
      val result = for {
        config <- getConfig[QuartzConfig]
      } yield assert(config)(equalTo(QuartzConfig(Quartz(ThreadPool(1, 5, true), "UTC"))))
      result.provideLayer(QuartzConfig.live)
    }
  )
}
