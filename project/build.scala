import play.Project._
import sbt.Keys._
import sbt._
import java.net.URI
import com.typesafe.sbt.SbtScalariform.scalariformSettings
import org.scalastyle.sbt.ScalastylePlugin

object P2PScalaProto extends Build {

  val Organization = "momijikawa"
  val Name = "p2pscalaproto"
  val Version = "0.2.16" 
  val ScalaVersion = "2.10.2"    

  // << groupId >> %%  << artifactId >> % << version >>
  lazy val LibraryDependencies = Seq(
    "org.specs2" %% "specs2" % "1.13" % "test",
    "com.typesafe.akka" %% "akka-actor" % "2.2.3",
    "com.typesafe.akka" %% "akka-remote" % "2.2.3",
    "com.typesafe.akka" %% "akka-agent" % "2.2.3",
    "commons-codec" % "commons-codec" % "1.3",
    "org.scalaz" %% "scalaz-core" % "7.0.0",
    "org.scalaz" %% "scalaz-effect" % "7.0.0",
    "org.scalaz" %% "scalaz-typelevel" % "7.0.0",
    "org.scalaz" %% "scalaz-scalacheck-binding" % "7.0.0" % "test",
    "com.psyonik" %% "psyonik-upnp" % "0.0.1-SNAPSHOT",
    "org.pegdown" % "pegdown" % "1.0.2",
    "junit" % "junit" % "latest.integration" % "test",
    "org.mockito" % "mockito-all" % "1.9.5"
  )
 
  lazy val projectSettings = Seq(
    parallelExecution in Test := false,
    testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),
    organization := Organization,
    name := Name,
    version := Version,
    scalaVersion := ScalaVersion,
    resolvers += Classpaths.typesafeReleases,
    resolvers += "Momijikawa Maven repository on GitHub" at "http://windymelt.github.io/repo/",
    libraryDependencies ++= LibraryDependencies,
    initialCommands := "import momijikawa.p2pscalaproto._",
    initialCommands in console := "import scalaz._, Scalaz._"
  )

  lazy val project = Project(
    "P2PScalaProto",
    file("."),
    settings =
      Defaults.defaultSettings ++
        scalariformSettings ++
        ScalastylePlugin.Settings ++
        ScctPlugin.instrumentSettings ++
        projectSettings
  )
}

