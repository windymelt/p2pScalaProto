resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"

resolvers += "bintray-typesafe-repo" at "https://dl.bintray.com/typesafe/maven-releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.1")

addSbtPlugin("org.ensime" % "ensime-sbt" % "0.2.0")

addSbtPlugin("com.github.scct" % "sbt-scct" % "0.2.1")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.5.1")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.3.2")

resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.7.0-SNAPSHOT")
