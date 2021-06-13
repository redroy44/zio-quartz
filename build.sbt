import Dependencies._
import xerial.sbt.Sonatype._

val scala2Version = "2.13.6"

def publishSettings(publishArtifacts: Boolean) = {
  val publishSettings = Seq(
    pomIncludeRepository := { _ => false },
    // publishMavenStyle := true,
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
    sonatypeProfileName := "io.github.redroy44",
    sonatypeCredentialHost := "s01.oss.sonatype.org"
  )
  val skipSettings    = Seq(
    publish / skip := true,
    publishArtifact := false
  )
  if (publishArtifacts) publishSettings else publishSettings ++ skipSettings
}

def stdSettings(prjName: String) = Seq(
  name := s"$prjName",
  ThisBuild / scalaVersion := scala2Version,
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  ThisBuild / scalafixScalaBinaryVersion := "2.13",
  ThisBuild / scalafixDependencies ++= List(
    "com.github.liancheng" %% "organize-imports" % "0.5.0",
    "com.github.vovapolu"  %% "scaluzzi"         % "0.1.18"
  ),
  Test / parallelExecution := true
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias(
  "check",
  "; scalafmtSbtCheck; scalafmtCheck; test:scalafmtCheck; compile:scalafix --check; test:scalafix --check"
)

lazy val root = (project in file("."))
  .settings(stdSettings("root"))
  .settings(publishSettings(false))
  .aggregate(zioQuartz, examples, docs)

lazy val zioQuartz = (project in file("zio-quartz-core"))
  .settings(
    organization := "io.github.redroy44",
    homepage := Some(url("https://redroy44.github.io/zio-quartz")),
    description := "Some description of the project",
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "redroy44",
        "Piotr Bandurski",
        "redroy44@gmail.com",
        url("https://github.com/redroy44")
      )
    ),
    ThisBuild / versionScheme := Some("early-semver")
  )
  .settings(stdSettings("zio-quartz"))
  .settings(publishSettings(true))
  .settings(
    libraryDependencies ++= Seq(
      `dev.zio`.zio,
      `dev.zio`.test,
      `dev.zio`.`test-sbt`,
      `dev.zio`.config,
      `dev.zio`.`config_typesafe`,
      `dev.zio`.`config_magnolia`,
      `dev.zio`.logging,
      `org.quartz-scheduler`.quartz
    ),
    scalacOptions := Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-Ywarn-unused:params,-implicits",
      "-language:higherKinds",
      "-language:existentials",
      "-explaintypes",
      "-Yrangepos",
      "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    ) ++ {
      if (sys.env.contains("CI")) {
        Seq("-Xfatal-warnings")
      } else {
        Nil // to enable Scalafix locally
      }
    },
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val examples = Project("examples", file("examples"))
  .settings(stdSettings("examples"))
  .settings(publishSettings(false))
  .aggregate(simple)

lazy val simple = Project("simple", file("examples") / "simple")
  .settings(stdSettings("simple"))
  .settings(publishSettings(false))
  .disablePlugins(ScalafixPlugin)
  .dependsOn(
    zioQuartz
  )

lazy val docs = project
  .in(file("zio-quartz-docs"))
  .settings(stdSettings("docs"))
  .settings(publishSettings(false))
  .settings(
    moduleName := "zio-quartz-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      `dev.zio`.zio
    ),
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioQuartz),
    ScalaUnidoc / unidoc / target := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value
  )
  .dependsOn(zioQuartz)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)
