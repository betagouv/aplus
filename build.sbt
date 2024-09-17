name := "aplus"
organization := "fr.gouv.beta"

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      "gitHeadCommit" -> git.gitHeadCommit.value,
      "gitHeadCommitDate" -> git.gitHeadCommitDate.value
    ),
    buildInfoPackage := "constants",
    // Test with Firefox
    // Note that sbt does not forward system properties from the cli to the tests
    Test / javaOptions += ("-Dwebdriver.gecko.driver=" + scala.sys.env("GECKO_DRIVER"))
  )
  .dependsOn(macrosProject)

inThisBuild(
  List(
    scalaVersion := "3.3.3",
    // The following setting are for scalafix
    semanticdbEnabled := true, // enable SemanticDB
    semanticdbVersion := scalafixSemanticdb.revision // use Scalafix compatible version
  )
)

// Options ref https://docs.scala-lang.org/scala3/guides/migration/options-intro.html
// Note: the Play sbt plugin adds some options
// https://github.com/playframework/playframework/blob/4c2d76095c2f6a5cab7be3e8f6017c4a4dd2a99f/dev-mode/sbt-plugin/src/main/scala/play/sbt/PlaySettings.scala#L89
scalacOptions ++= Seq(
  "-feature",
  // "-deprecation", // Added by Play
  // "-unchecked", // Added by Play
  // Sets warnings as errors on the CI
  if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
  // Recommended for cats-effect
  // https://typelevel.org/cats-effect/docs/getting-started
  // "-Wnonunit-statement", // TODO: the route warnings needs to be silenced
  // Note: To show all possible warns, use "-W"
  // TODO:  3.3.4 should have the src filter
  // https://github.com/scala/scala3/pull/21087/files
  // https://github.com/playframework/playframework/issues/6302
  // "-Wunused:imports", // gives non useful warns due to twirl
  "-Wunused:privates",
  "-Wunused:locals",
  // "-Wunused:explicits", // TODO: lot of warnings, enable later
  "-Wunused:implicits",
  "-Wunused:nowarn",
  "-Wvalue-discard",
)

val anormVersion = "2.7.0"

lazy val anormDependency = "org.playframework.anorm" %% "anorm" % anormVersion

lazy val anormDependencies = Seq(
  anormDependency,
  "org.playframework.anorm" %% "anorm-postgres" % anormVersion,
)

libraryDependencies ++= Seq(
  ws,
  jdbc,
  evolutions,
)

pipelineStages := Seq(digest, gzip)

libraryDependencies += guice

val fs2Version = "3.11.0"

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.7.4",
  "org.playframework" %% "play-mailer" % "10.0.0",
  "org.playframework" %% "play-json" % "3.0.4",
  "com.sun.mail" % "javax.mail" % "1.6.2",
  "net.jcazevedo" %% "moultingyaml" % "0.4.2" cross CrossVersion.for3Use2_13,
  "com.github.tototoshi" %% "scala-csv" % "2.0.0",
  ws,
  "com.lihaoyi" %% "scalatags" % "0.13.1",
  "org.typelevel" %% "cats-core" % "2.12.0",
  "org.typelevel" %% "cats-effect" % "3.5.4",
  "co.fs2" %% "fs2-core" % fs2Version,
  "co.fs2" %% "fs2-io" % fs2Version,
  "io.laserdisc" %% "fs2-aws-s3" % "6.1.3",
  // Needs the C library to be installed
  "de.mkammerer" % "argon2-jvm-nolibs" % "2.11",
)

libraryDependencies ++= anormDependencies

// UI
libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "3.0.1",
  "org.webjars.bower" % "material-design-lite" % "1.3.0",
  "org.webjars" % "material-design-icons" % "4.0.0",
  "org.webjars.npm" % "roboto-fontface" % "0.10.0",
  "org.webjars" % "font-awesome" % "6.5.2",
)

// Crash
libraryDependencies += "io.sentry" % "sentry-logback" % "7.14.0"

// Sync with Play and scala-steward pin
// https://github.com/playframework/playframework/blob/4c2d76095c2f6a5cab7be3e8f6017c4a4dd2a99f/project/Dependencies.scala#L20
val specs2Version = "4.20.8"

// Test
libraryDependencies ++= Seq(
  specs2 % Test, // Play Plugin
  "org.specs2" %% "specs2-scalacheck" % specs2Version % Test,
  "org.scalacheck" %% "scalacheck" % "1.18.0" % Test,
  "org.typelevel" %% "cats-effect-testing-specs2" % "1.5.0" % Test,
)

// Overrides
dependencyOverrides += "org.apache.commons" % "commons-text" % "1.10.0"

// Note: we force snakeyaml version here because moultingyaml is not updated
dependencyOverrides += "org.yaml" % "snakeyaml" % "1.33"

// Sync with scala-steward pin
val jacksonVersion = "2.14.3"
val jacksonDatabindVersion = "2.14.3"

val jacksonOverrides = Seq(
  "com.fasterxml.jackson.core" % "jackson-core",
  "com.fasterxml.jackson.core" % "jackson-annotations",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"
).map(_ % jacksonVersion)

val jacksonDatabindOverrides = Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion
)

val akkaSerializationJacksonOverrides = Seq(
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor",
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names",
  "com.fasterxml.jackson.module" %% "jackson-module-scala",
).map(_ % jacksonVersion)

libraryDependencies ++= jacksonDatabindOverrides ++ jacksonOverrides ++ akkaSerializationJacksonOverrides

// Adds additional packages into Twirl
TwirlKeys.templateImports += "constants.Constants"
TwirlKeys.templateImports += "_root_.helper.TwirlImports._"
TwirlKeys.templateImports += "views.MainInfos"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "fr.gouv.beta.binders._"

// See https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

/////////////////////////////////////
//              Macros             //
/////////////////////////////////////

lazy val macrosProject = (project in file("macros"))
  .settings(
    libraryDependencies ++= Seq(
      anormDependency,
    )
  )

/////////////////////////////////////
// Task and Hook for the TS build  //
/////////////////////////////////////

/** This task will run `npm install` and `npm run build` in the typescript/ directory. This will
  * generate the JS production bundle in public/generate-js/index.js
  */
lazy val npmBuild = taskKey[Unit]("Run npm run build.")

npmBuild := {
  import scala.sys.process.Process
  val tsDirectory = baseDirectory.value
  println("Running npm install in directory " + tsDirectory)
  Process("npm install", tsDirectory).!
  Process("npm run build", tsDirectory).!
}

/** Add a hook to the Play sbt plugin, so `npm run watch` is runned when using the sbt command `run`
  *
  * See documentation:
  * https://www.playframework.com/documentation/2.8.x/sbtCookbook#Hooking-into-Plays-dev-mode
  * https://github.com/playframework/playframework/blob/2.8.x/documentation/manual/working/commonGuide/build/code/runhook.sbt
  */
def NpmWatch(base: File) = {
  import play.sbt.PlayRunHook
  import sbt._
  import scala.sys.process.Process

  object NpmWatch {

    def apply(base: File): PlayRunHook = {

      object NpmProcess extends PlayRunHook {

        var watchProcess: Option[Process] = None

        override def beforeStarted(): Unit =
          Process("npm install", base).run

        override def afterStarted(): Unit =
          watchProcess = Some(Process("npm run watch", base).run)

        override def afterStopped(): Unit = {
          watchProcess.map(p => p.destroy())
          watchProcess = None
        }

      }

      NpmProcess
    }

  }

  NpmWatch(base)
}

PlayKeys.playRunHooks += NpmWatch(baseDirectory.value)
