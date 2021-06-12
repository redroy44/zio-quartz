package io.github.redroy44.zioquartz.core

import com.typesafe.config.ConfigFactory
import zio._
import zio.config._
import zio.config.magnolia._
import zio.config.typesafe.TypesafeConfigSource

final case class ThreadPool(threadCount: Int, threadPriority: Int, daemonThreads: Boolean)

final case class Quartz(threadPool: ThreadPool, defaultTimezone: String)

final case class QuartzConfig(quartz: Quartz)

object QuartzConfig {
  val autoDescriptor: ConfigDescriptor[QuartzConfig] = DeriveConfigDescriptor.descriptor[QuartzConfig]

  val live: ZLayer[Any, ReadError[String], Has[QuartzConfig]] = ZIO
    .fromEither(
      TypesafeConfigSource
        .fromTypesafeConfig(ConfigFactory.defaultReference())
        .flatMap(source => read(autoDescriptor from source))
    )
    .toLayer
}
