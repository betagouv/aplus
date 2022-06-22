name := "aplus"
organization := "fr.gouv.beta"

version := "1.0-SNAPSHOT"

// https://gitlab.com/kpmeen/clammyscan
lazy val clammyStreams = ProjectRef(
  uri(s"https://gitlab.com/kpmeen/clammyscan.git#6a98b2e836a1991e892ef2556d674263d5d80df7"),
  "clammyscan-streams"
)

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
  .dependsOn(clammyStreams)

inThisBuild(
  List(
    scalaVersion := "2.13.8",
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
  // "-Wunused:imports",
  "-Wunused:patvars",
  "-Wunused:privates",
  "-Wunused:locals",
  // "-Wunused:explicits", TODO: lot of warnings, enable later
  "-Wunused:implicits",
  "-Wvalue-discard"
)

lazy val anormDependency = "org.playframework.anorm" %% "anorm" % "2.6.10"

libraryDependencies ++= Seq(
  ws,
  jdbc,
  evolutions,
  ehcache
)

pipelineStages := Seq(digest, gzip)

libraryDependencies += specs2 % Test
libraryDependencies += guice

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.3.6",
  anormDependency,
  "com.typesafe.play" %% "play-mailer" % "8.0.1",
  "com.sun.mail" % "javax.mail" % "1.6.2",
  "net.jcazevedo" %% "moultingyaml" % "0.4.2",
  "com.github.tototoshi" %% "scala-csv" % "1.3.10",
  ws,
  "com.lihaoyi" %% "scalatags" % "0.11.1",
  "org.typelevel" %% "cats-core" % "2.7.0",
  // To ensure that the version of jackson that do not have
  // known security vulnerabilities is used
  // It is also compatible with play-json
  // https://github.com/playframework/play-json/blob/main/build.sbt#L34
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.11.4"
)

// UI
libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.8.13",
  "org.webjars.bower" % "material-design-lite" % "1.3.0",
  "org.webjars" % "material-design-icons" % "4.0.0",
  "org.webjars.npm" % "roboto-fontface" % "0.10.0",
  "org.webjars" % "chartjs" % "2.9.4",
  "org.webjars" % "font-awesome" % "6.1.1",
)
// Crash
libraryDependencies += "io.sentry" % "sentry-logback" % "5.7.4"

// Adds additional packages into Twirl
TwirlKeys.templateImports += "constants.Constants"
TwirlKeys.templateImports += "_root_.helper.TwirlImports._"
TwirlKeys.templateImports += "views.MainInfos"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "fr.gouv.beta.binders._"

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
