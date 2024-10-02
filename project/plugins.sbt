// The Play plugin
// Important note: when upgrading the play version, check that the correct minor version
//                 of jackson is set in build.sbt (with the patch version that is not vulnerable)
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.5")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.2.1")
addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.0.0")
addSbtPlugin("com.github.sbt" % "sbt-gzip" % "2.0.0")

// Scalafmt
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")

// Git to get the current git commit
addSbtPlugin("com.github.sbt" % "sbt-git" % "2.0.1")

// Makes available sbt commands results to project scala code
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.13.0")

// See https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
