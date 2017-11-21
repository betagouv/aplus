name := """aplus"""
organization := "fr.gouv.beta"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.4"

//libraryDependencies += filters

libraryDependencies ++= Seq(
  ws
)

libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
libraryDependencies += guice

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.6.1",
  "org.webjars.bower" % "material-design-lite" % "1.3.0",
  "org.webjars.bower" % "material-design-icons" % "3.0.1",
  "org.postgresql" % "postgresql" % "9.4.1210",
  "com.typesafe.play" %% "anorm" % "2.5.3",
  "com.typesafe.play" %% "play-mailer" % "6.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.1"
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "fr.gouv.beta.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "fr.gouv.beta.binders._"
