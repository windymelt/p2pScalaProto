name := "P2PScalaProto"

organization := "momijikawa"

version := "0.2.5"

scalaVersion := "2.10.2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Momijikawa Maven repository on GitHub" at "http://windymelt.github.io/repo/"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2" % "1.13" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "com.typesafe.akka" %% "akka-remote" % "2.2.3",
  "com.typesafe.akka" %% "akka-agent" % "2.2.3",
  "commons-codec" % "commons-codec" % "1.3",
  "org.scalaz" %% "scalaz-core" % "7.0.0",
  "org.scalaz" %% "scalaz-effect" % "7.0.0",
  "org.scalaz" %% "scalaz-typelevel" % "7.0.0",
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.0.0" % "test",
  "com.psyonik" %% "psyonik-upnp" % "0.0.1-SNAPSHOT"
)

initialCommands := "import momijikawa.p2pscalaproto._"

initialCommands in console := "import scalaz._, Scalaz._"

publishTo := Some(Resolver.file("p2p2ch",file(Path.userHome.absolutePath+"/Documents/programme/repo"))(Patterns(true, Resolver.mavenStyleBasePattern)))