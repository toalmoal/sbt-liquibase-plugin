addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.12.2")
addSbtPlugin("com.github.gseitz" % "sbt-release"  % "1.0.11")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
