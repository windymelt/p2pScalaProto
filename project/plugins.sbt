resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"

resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"

resolvers += "bintray-typesafe-repo" at "https://dl.bintray.com/typesafe/maven-releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.1")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")

addSbtPlugin("org.ensime" % "ensime-sbt" % "0.1.5")

addSbtPlugin("com.github.scct" % "sbt-scct" % "0.2.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.3.2")
