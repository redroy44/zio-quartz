package io.github.redroy44.zioquartz.core

import org.quartz._
import zio._

class SimpleQuartzJob[T] extends Job {
  def execute(context: JobExecutionContext): Unit = {
    val dataMap = context.getJobDetail.getJobDataMap
    // val jobKey  = context.getJobDetail.getKey

    val receiver = dataMap.get("receiver").asInstanceOf[Queue[T]]
    val msg      = dataMap.get("message").asInstanceOf[T]

    val runtime = Runtime.default

    // val execute = receiver match {
    //   case q: Queue[T] => q.offer(msg)
    //   case _                => ZIO.fail(new RuntimeException("errrrrorrr!!!!!"))
    // }

    runtime.unsafeRun(receiver.offer(msg).unit)

  }

}
