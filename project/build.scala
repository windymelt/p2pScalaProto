import play.Project._
import sbt.Keys._
import sbt._
import java.net.URI
import com.typesafe.sbt.SbtScalariform.scalariformSettings
import org.scalastyle.sbt.ScalastylePlugin

object P2PScalaProto extends Build {

  val Organization = "momijikawa"
  val Name = "p2pscalaproto"
  val Version       = "0.3.0" 
  val ScalaVersion  = "2.10.4"
  val ScalazVersion = "7.1.1"
  val AkkaVersion   = "2.2.3"

  // << groupId >> %%  << artifactId >> % << version >>
  lazy val LibraryDependencies = Seq(
    "com.typesafe.akka" %% "akka-actor"  % AkkaVersion,
    "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
    "com.typesafe.akka" %% "akka-agent"  % AkkaVersion,
    "org.scalaz" %% "scalaz-core"               % ScalazVersion,
    "org.scalaz" %% "scalaz-effect"             % ScalazVersion,
    "org.scalaz" %% "scalaz-typelevel"          % ScalazVersion,
    "org.scalaz" %% "scalaz-scalacheck-binding" % ScalazVersion % "test",
    "org.specs2" %% "specs2-core" % "3.1" % "test",
    "commons-codec" % "commons-codec" % "1.9",
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
    resolvers += DefaultMavenRepository,
    resolvers += Classpaths.typesafeReleases,
    resolvers += "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases",
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

