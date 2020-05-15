name := """aplus"""
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
    buildInfoPackage := "constants"
  )

// TODO: when upgrading the version, remove "-Wconf:msg=Octal:s"
scalaVersion := "2.13.2"

// https://docs.scala-lang.org/overviews/compiler-options/index.html
scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-Xlint:adapted-args",
  "-Xlint:nullary-unit",
  "-Xlint:inaccessible",
  "-Xlint:nullary-override",
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
  "-Wdead-code",
  "-Wextra-implicit",
  "-Wmacros:before",
  "-Wnumeric-widen",
  "-Woctal-literal",
  // Fixes a regression in 2.13.2:
  // https://github.com/scala/bug/issues/11950
  "-Wconf:msg=Octal:s",
  // "-Wself-implicit", // Warns about too much useful constructs
  // Note: -Wunused:imports cannot work with twirl
  // "-Wunused:imports",
  "-Wunused:patvars",
  "-Wunused:privates",
  "-Wunused:locals",
  // "-Wunused:explicits", TODO: lot of warnings, enable later
  "-Wunused:implicits",
  "-Wvalue-discard",
)

libraryDependencies ++= Seq(
  ws,
  jdbc,
  evolutions,
  ehcache
)

pipelineStages := Seq(rjs, digest, gzip)

libraryDependencies += specs2 % Test
libraryDependencies += guice

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.2.12",
  "org.playframework.anorm" %% "anorm" % "2.6.5",
  "com.typesafe.play" %% "play-mailer" % "7.0.1",
  "com.sun.mail" % "javax.mail" % "1.6.2",
  "com.typesafe.play" %% "play-mailer-guice" % "7.0.1",
  "net.jcazevedo" %% "moultingyaml" % "0.4.2",
  "com.google.guava" % "guava" % "28.1-jre",
  "com.github.tototoshi" %% "scala-csv" % "1.3.6",
  ws
)

// UI
libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.8.0",
  "org.webjars.bower" % "material-design-lite" % "1.3.0",
  "org.webjars" % "material-design-icons" % "3.0.1",
  "org.webjars.npm" % "roboto-fontface" % "0.10.0",
  "org.webjars.npm" % "slim-select" % "1.24.0",
  "org.webjars.npm" % "dialog-polyfill" % "0.4.10",
  "org.webjars.npm" % "twemoji" % "2.5.1",
  "org.webjars" % "chartjs" % "2.9.3",
  "org.webjars" % "font-awesome" % "5.13.0",
  "org.webjars.bowergithub.olifolkerd" % "tabulator" % "4.5.3",
  "org.webjars.npm" % "xlsx" % "0.15.5"
)
// Crash
libraryDependencies += "io.sentry" % "sentry-logback" % "1.7.30"

// Adds additional packages into Twirl
TwirlKeys.templateImports += "constants.Constants"
TwirlKeys.templateImports += "_root_.helper.TwirlImports._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "fr.gouv.beta.binders._"
