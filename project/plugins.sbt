addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.1")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.7")
addSbtPlugin("com.github.gseitz" % "sbt-release"  % "1.0.11")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
