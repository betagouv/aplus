// The Play plugin
// Important note: when upgrading the play version, check that the correct minor version
//                 of jackson is set in build.sbt (with the patch version that is not vulnerable)
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.13")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.9.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")

// Scalafmt
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")

// Git to get the current git commit
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.2")

// Makes available sbt commands results to project scala code
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.33")
