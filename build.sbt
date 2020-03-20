name := """aplus"""
organization := "fr.gouv.beta"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

// https://docs.scala-lang.org/overviews/compiler-options/index.html
scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-Xlint:adapted-args,nullary-unit,inaccessible,nullary-override,infer-any,missing-interpolator,private-shadow,type-parameter-shadow,poly-implicit-overload,option-implicit,package-object-classes,unused",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen"
)

//libraryDependencies += filters

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
  "org.postgresql" % "postgresql" % "42.2.11",
  "org.playframework.anorm" %% "anorm" % "2.6.5",
  "com.typesafe.play" %% "play-mailer" % "7.0.1",
  "com.sun.mail" % "javax.mail" % "1.6.2",
  "com.typesafe.play" %% "play-mailer-guice" % "7.0.1",
  "net.jcazevedo" %% "moultingyaml" % "0.4.0",
  "com.google.guava" % "guava" % "28.1-jre",
  "com.github.tototoshi" %% "scala-csv" % "1.3.6",
  ws
)

// UI
libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.6.3",
  "org.webjars.bower" % "material-design-lite" % "1.3.0",
  "org.webjars" % "material-design-icons" % "3.0.1",
  "org.webjars.npm" % "roboto-fontface" % "0.10.0",
  "org.webjars.npm" % "slim-select" % "1.24.0",
  "org.webjars.npm" % "dialog-polyfill" % "0.4.10",
  "org.webjars.npm" % "twemoji" % "2.5.1",
  "org.webjars" % "chartjs" % "2.9.3",
  "org.webjars" % "font-awesome" % "5.12.0",
  "org.webjars.bowergithub.olifolkerd" % "tabulator" % "4.5.3",
  "org.webjars.npm" % "xlsx" % "0.15.5"
)
// Crash
libraryDependencies += "io.sentry" % "sentry-logback" % "1.7.30"

// Adds additional packages into Twirl
TwirlKeys.templateImports += "constants.Constants"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "fr.gouv.beta.binders._"
