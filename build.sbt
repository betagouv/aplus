name := """aplus"""
organization := "fr.gouv.beta"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.4"

//libraryDependencies += filters

libraryDependencies ++= Seq(
  ws,
  jdbc,
  evolutions
)

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
libraryDependencies += guice

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.6.1",
  "org.webjars.bower" % "material-design-lite" % "1.3.0",
  "org.webjars" % "material-design-icons" % "3.0.1",
  "org.webjars.npm" % "roboto-fontface" % "0.8.0",
  "org.postgresql" % "postgresql" % "42.1.4",
  "com.typesafe.play" %% "anorm" % "2.5.3",
  "com.typesafe.play" %% "play-mailer" % "6.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.1",
)

libraryDependencies += "io.sentry" % "sentry-logback" % "1.6.3"

// Adds additional packages into Twirl

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "fr.gouv.beta.binders._"
