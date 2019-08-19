name := """ass2"""
organization := "com.mypro"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.12.8"

libraryDependencies += guice

libraryDependencies += "com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.1"
libraryDependencies += "org.webjars" %% "webjars-play" % "2.7.0"
libraryDependencies += "org.webjars" % "flot" % "0.8.3"
libraryDependencies += "org.webjars" % "bootstrap" % "3.3.6"

libraryDependencies += guice
libraryDependencies += ws

libraryDependencies += ehcache
libraryDependencies += "org.assertj" % "assertj-core" % "3.8.0" % Test
libraryDependencies += "org.awaitility" % "awaitility" % "3.0.0" % Test

// Needed to make JUnit report the tests being run
testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v"))

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-remote" % "2.5.22"
)


javacOptions ++= Seq(
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)
