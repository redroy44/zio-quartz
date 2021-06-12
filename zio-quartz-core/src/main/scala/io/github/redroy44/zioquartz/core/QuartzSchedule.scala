package io.github.redroy44.zioquartz.core

import org.quartz._

import java.util.{Date, TimeZone}

sealed trait QuartzSchedule {
  type T <: Trigger
  def name: String
  def description: Option[String]
  def schedule: ScheduleBuilder[T]
  def calendar: Option[String]

  def buildTrigger(name: String, futureDate: Option[Date]): T = {
    val partialTriggerBuilder = TriggerBuilder
      .newTrigger()
      .withIdentity(name + "_Trigger")
      .withDescription(description.orNull)
      .withSchedule(schedule)

    var triggerBuilder = futureDate
      .fold(partialTriggerBuilder.startNow)(partialTriggerBuilder.startAt)

    triggerBuilder = calendar.fold(triggerBuilder)(triggerBuilder.modifiedByCalendar)
    triggerBuilder.build()
  }
}

final case class QuartzCronSchedule(
  name: String,
  description: Option[String] = None,
  expression: CronExpression,
  timezone: TimeZone,
  calendar: Option[String] = None
) extends QuartzSchedule {
  type T = CronTrigger

  val schedule: CronScheduleBuilder = CronScheduleBuilder.cronSchedule(expression).inTimeZone(timezone)
}

final case class SimpleSchedule(
  name: String,
  description: Option[String] = None,
  timezone: TimeZone,
  calendar: Option[String] = None
) extends QuartzSchedule {
  type T = SimpleTrigger

  val schedule: SimpleScheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
}
