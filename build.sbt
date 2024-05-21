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
    scalaVersion := "2.13.14",
    semanticdbEnabled := true, // enable SemanticDB
    semanticdbVersion := scalafixSemanticdb.revision // use Scalafix compatible version
  )
)

// https://docs.scala-lang.org/overviews/compiler-options/index.html
scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-Xlint:adapted-args",
  "-Xlint:nullary-unit",
  "-Xlint:inaccessible",
  "-Xlint:infer-any",
  "-Xlint:missing-interpolator",
  "-Xlint:doc-detached",
  "-Xlint:private-shadow",
  "-Xlint:type-parameter-shadow",
  "-Xlint:poly-implicit-overload",
  "-Xlint:option-implicit",
  "-Xlint:delayedinit-select",
  "-Xlint:package-object-classes",
  "-Xlint:stars-align",
  "-Xlint:constant",
  // Note: -Xlint:unused cannot work with twirl
  // "-Xlint:unused",
  "-Xlint:nonlocal-return",
  "-Xlint:implicit-not-found",
  "-Xlint:serial",
  "-Xlint:valpattern",
  "-Xlint:eta-zero",
  "-Xlint:eta-sam",
  "-Xlint:deprecation",
  // Sets warnings as errors on the CI
  if (insideCI.value) "-Wconf:any:error" else "-Wconf:any:warning",
  "-Wdead-code",
  "-Wextra-implicit",
  "-Wmacros:before",
  "-Wnumeric-widen",
  "-Woctal-literal",
  // "-Wself-implicit", // Warns about too much useful constructs
  // Note: -Wunused:imports cannot work with twirl
  "-Wunused:imports",
  "-Wunused:patvars",
  "-Wunused:privates",
  "-Wunused:locals",
  // "-Wunused:explicits", TODO: lot of warnings, enable later
  "-Wunused:implicits",
  "-Wconf:cat=unused&src=routes/.*:s",
  "-Wconf:cat=unused&src=twirl/.*:s",
  "-Wvalue-discard"
)

// https://typelevel.org/cats-effect/docs/getting-started
addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

lazy val anormDependency = "org.playframework.anorm" %% "anorm" % "2.7.0"

libraryDependencies ++= Seq(
  ws,
  jdbc,
  evolutions,
)

pipelineStages := Seq(digest, gzip)

libraryDependencies += specs2 % Test
libraryDependencies += guice

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.7.3",
  anormDependency,
  "org.playframework" %% "play-mailer" % "10.0.0",
  "org.playframework" %% "play-json" % "3.0.3",
  "com.sun.mail" % "javax.mail" % "1.6.2",
  "net.jcazevedo" %% "moultingyaml" % "0.4.2",
  "com.github.tototoshi" %% "scala-csv" % "1.3.10",
  ws,
  "com.lihaoyi" %% "scalatags" % "0.12.0",
  "org.typelevel" %% "cats-core" % "2.10.0",
  "org.typelevel" %% "cats-effect" % "3.5.4",
)

// UI
libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "3.0.1",
  "org.webjars.bower" % "material-design-lite" % "1.3.0",
  "org.webjars" % "material-design-icons" % "4.0.0",
  "org.webjars.npm" % "roboto-fontface" % "0.10.0",
  "org.webjars" % "font-awesome" % "6.5.2",
)

// Crash
libraryDependencies += "io.sentry" % "sentry-logback" % "7.9.0"

// Overrides
dependencyOverrides += "org.apache.commons" % "commons-text" % "1.10.0"

// Note: we force snakeyaml version here because moultingyaml is not updated
dependencyOverrides += "org.yaml" % "snakeyaml" % "1.33"

// Jackson CVE fix
// https://github.com/playframework/playframework/discussions/11222
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

lazy val scalaReflect = Def.setting("org.scala-lang" % "scala-reflect" % scalaVersion.value)

lazy val macrosProject = (project in file("macros"))
  .settings(
    libraryDependencies ++= Seq(
      anormDependency,
      scalaReflect.value
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
