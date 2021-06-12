import sbt._

object Dependencies {

  case object `dev.zio` {
    val zio               = "dev.zio" %% "zio"                 % "1.0.9"
    val test              = "dev.zio" %% "zio-test"            % "1.0.9" % "test"
    val `test-sbt`        = "dev.zio" %% "zio-test-sbt"        % "1.0.9" % "test"
    val config            = "dev.zio" %% "zio-config"          % "1.0.6"
    val `config_typesafe` = "dev.zio" %% "zio-config-typesafe" % "1.0.6"
    val `config_magnolia` = "dev.zio" %% "zio-config-magnolia" % "1.0.6"
    val logging           = "dev.zio" %% "zio-logging"         % "0.5.10"
  }

  case object `org.quartz-scheduler` {
    val quartz = "org.quartz-scheduler" % "quartz" % "2.3.2" exclude ("com.zaxxer", "HikariCP-java7")
  }
}
