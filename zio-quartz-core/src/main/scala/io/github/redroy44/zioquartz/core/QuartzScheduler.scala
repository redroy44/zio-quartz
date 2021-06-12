package io.github.redroy44.zioquartz.core

import org.quartz.core.jmx.JobDataMapSupport
import org.quartz.impl.DirectSchedulerFactory
import org.quartz.simpl.{RAMJobStore, SimpleThreadPool}
import org.quartz.{Scheduler, _}
import zio.clock.Clock
import zio.config._
import zio.logging._
import zio.{Task, _}

import java.util.{Date, TimeZone}

trait QuartzScheduler {

  def getSchedules: UIO[Map[String, QuartzSchedule]]

  def standby: Task[Unit]
  def resume: Task[Boolean]
  def suspendAll: Task[Unit]
  def suspendJob: Task[Boolean]
  def resumeJob: Task[Boolean]
  def resumeAll: Task[Unit]
  def cancelJob: Task[Boolean]
  def createJobSchedule[T](
    name: String,
    receiver: Queue[T],
    msg: T,
    cronExpression: String,
    description: Option[String],
    calendar: Option[String],
    timezone: TimeZone
  ): Task[Date]
  // def updateJobSchedule
  // def deleteJobSchedule
  // def unscheduleJob
  def createSchedule(
    name: String,
    cronExpression: String,
    description: Option[String],
    calendar: Option[String],
    timezone: TimeZone
  ): Task[Unit]
  // def rescheduleJob
  // def removeSchedule
  def schedule[T](name: String, receiver: Queue[T], msg: T, startDate: Option[Date]): Task[Date]
}

object QuartzScheduler {
  // val live: TaskLayer[Has[QuartzScheduler]] = QuartzSchedulerLive.layer

  def getSchedules: RIO[Has[QuartzScheduler], Map[String, QuartzSchedule]] =
    ZIO.serviceWith[QuartzScheduler](_.getSchedules)

  def standby: RIO[Has[QuartzScheduler], Unit]       = ZIO.serviceWith[QuartzScheduler](_.standby)
  def resume: RIO[Has[QuartzScheduler], Boolean]     = ZIO.serviceWith[QuartzScheduler](_.resume)
  def suspendAll: RIO[Has[QuartzScheduler], Unit]    = ZIO.serviceWith[QuartzScheduler](_.suspendAll)
  def suspendJob: RIO[Has[QuartzScheduler], Boolean] = ZIO.serviceWith[QuartzScheduler](_.suspendJob)
  def resumeJob: RIO[Has[QuartzScheduler], Boolean]  = ZIO.serviceWith[QuartzScheduler](_.resumeJob)
  def resumeAll: RIO[Has[QuartzScheduler], Unit]     = ZIO.serviceWith[QuartzScheduler](_.resumeAll)
  def cancelJob: RIO[Has[QuartzScheduler], Boolean]  = ZIO.serviceWith[QuartzScheduler](_.cancelJob)
  def createJobSchedule[T](
    name: String,
    receiver: Queue[T],
    msg: T,
    cronExpression: String,
    description: Option[String] = None,
    calendar: Option[String] = None,
    timezone: TimeZone = TimeZone.getTimeZone("UTC")
  ): RIO[Has[QuartzScheduler], Date]                 = ZIO.serviceWith[QuartzScheduler](
    _.createJobSchedule(name, receiver, msg, cronExpression, description, calendar, timezone)
  )

  def createSchedule(
    name: String,
    cronExpression: String,
    description: Option[String] = None,
    calendar: Option[String] = None,
    timezone: TimeZone = TimeZone.getTimeZone("UTC")
  ): RIO[Has[QuartzScheduler], Unit] =
    ZIO.serviceWith[QuartzScheduler](_.createSchedule(name, cronExpression, description, calendar, timezone))

  def schedule[T](
    name: String,
    receiver: Queue[T],
    msg: T,
    startDate: Option[Date] = None
  ): RIO[Has[QuartzScheduler], Date] =
    ZIO.serviceWith[QuartzScheduler](_.schedule(name, receiver, msg, startDate))
}

case class QuartzSchedulerLive(
  logger: Logger[String],
  scheduler: Scheduler,
  schedules: Ref[Map[String, QuartzSchedule]],
  runningJobs: Ref[Map[String, JobKey]]
) extends QuartzScheduler {

  override def createJobSchedule[T](
    name: String,
    receiver: Queue[T],
    msg: T,
    cronExpression: String,
    description: Option[String],
    calendar: Option[String],
    timezone: TimeZone
  ): Task[Date] = for {
    _ <- createSchedule(name, cronExpression, description, calendar, timezone)
    s <- schedule(name, receiver, msg)
  } yield s

  def getSchedules: UIO[Map[String, QuartzSchedule]] = schedules.get

  def createSchedule(
    name: String,
    cronExpression: String,
    description: Option[String],
    calendar: Option[String],
    timezone: TimeZone
  ): Task[Unit] = for {
    cron    <- ZIO.effect(new CronExpression(cronExpression))
    schedule = QuartzCronSchedule(name, description, cron, timezone, calendar)
    _       <- schedules.update(s => s.updated(name, schedule))
  } yield ()

  override def suspendJob: Task[Boolean] = ???

  override def resumeJob: Task[Boolean] = ???

  override def resumeAll: Task[Unit] = ???

  override def cancelJob: Task[Boolean] = ???

  def standby: Task[Unit] = ZIO.effect(scheduler.standby())

  def resume: Task[Boolean] =
    (scheduler.isInStandbyMode match {
      case true  =>
        logger.warn("Cannot start scheduler, already started.") *>
          ZIO.effectTotal(false)
      case false =>
        ZIO.effect {
          scheduler.start
          true
        }
    })

  def suspendAll: Task[Unit] =
    logger.info("Suspending all Quartz jobs.") *>
      ZIO.effect(scheduler.pauseAll())

  def schedule[T](name: String, receiver: Queue[T], msg: T, startDate: Option[Date] = None): Task[Date] =
    scheduleInternal(name, receiver, msg, startDate)

  private def scheduleInternal[T](name: String, receiver: Queue[T], msg: T, startDate: Option[Date]): Task[Date] = for {
    schedules <- schedules.get
    schedule  <- ZIO.getOrFailWith(
                   new IllegalArgumentException(s"No matching quartz configuration found for schedule $name")
                 )(schedules.get(name))
    result    <- scheduleJob(name, receiver, msg, startDate)(schedule)
  } yield result

  private def scheduleJob[T](name: String, receiver: Queue[T], msg: T, startDate: Option[Date])(
    schedule: QuartzSchedule
  ): Task[Date] = {
    import scala.jdk.CollectionConverters._
    for {
      _          <- logger.info(s"Setting up scheduled job $name, with $schedule")
      jobDataMap <- ZIO.effectTotal(
                      Map[String, Any](
                        "receiver" -> receiver,
                        "message"  -> msg
                      )
                    )

      jobData <- ZIO.effectTotal(JobDataMapSupport.newJobDataMap(jobDataMap.asJava))
      job      = JobBuilder
                   .newJob(classOf[SimpleQuartzJob[T]])
                   .withIdentity(name + "_Job")
                   .usingJobData(jobData)
                   .withDescription(schedule.description.getOrElse(""))
                   .build()

      _       <- logger.debug(s"Adding jobKey ${job.getKey}")
      _       <- runningJobs.update(rj => rj.updated(name, job.getKey))
      _       <- logger.debug(s"Building Trigger with startDate ${startDate.getOrElse(new Date())}")
      trigger <- ZIO.effect(schedule.buildTrigger(name, startDate))
      _       <- logger.debug(s"Scheduling Job $job and Trigger $trigger. Is Scheduler Running? ${scheduler.isStarted}")
      date    <- ZIO.effect(scheduler.scheduleJob(job, trigger))
    } yield date
  }
}

object QuartzSchedulerLive {
  val env: ZLayer[zio.console.Console with Clock with Any, ReadError[String], Logging with Has[QuartzConfig]] =
    Logging.console(LogLevel.Debug) ++ QuartzConfig.live

  val layer: RLayer[ZEnv, Has[QuartzScheduler]] = ({
    for {
      cfg         <- getConfig[QuartzConfig].toManaged_
      scheduler   <- Managed.make(acquire(cfg.quartz.threadPool))(release)
      schedules   <- Ref.make(Map[String, QuartzSchedule]()).toManaged_
      runningJobs <- Ref.make(Map[String, JobKey]()).toManaged_
      logger      <- ZIO.service[Logger[String]].toManaged_
    } yield QuartzSchedulerLive(logger, scheduler, schedules, runningJobs)
  }.provideLayer(env)).toLayer

  def acquire(cfg: ThreadPool): Task[Scheduler] = ZIO.effect {
    val jobStore = new RAMJobStore()
    val threadPool = {
      val tp = new SimpleThreadPool(cfg.threadCount, cfg.threadPriority)
      tp.setThreadNamePrefix("QUARTZ_")
      tp.setMakeThreadsDaemons(cfg.daemonThreads)
      tp
    }

    DirectSchedulerFactory.getInstance.createScheduler(threadPool, jobStore)
    val scheduler = DirectSchedulerFactory.getInstance.getScheduler
    scheduler.start
    scheduler
  }

  def release(scheduler: Scheduler): URIO[Any, Unit] = ZIO.effect(scheduler.shutdown).orDie

  def initializeCalendars() = ???

}
