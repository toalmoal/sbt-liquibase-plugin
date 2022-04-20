import xerial.sbt.Sonatype._
import ReleaseTransformations._

lazy val buildSettings = Seq(
  organization := "com.toalmoal",
  organizationName := "ToalMoal Private Ltd.",
  organizationHomepage := Some(new URL("https://toalmoal.com")),
  publishArtifact in Test := false,
  sbtPlugin := true,
  scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
  scriptedBufferLog := false,
  scriptedLaunchOpts := {
    scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
  },
  scalaVersion := "2.12.15",
  crossSbtVersions := Vector("0.13.16"),
  releaseCrossBuild := true,
  releaseTagName := {
    (version in ThisBuild).value
  },
  parallelExecution := true,

  sonatypeProfileName := "com.toalmoal",
  publishMavenStyle := true,
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/toalmoal/sbt-liquibase-plugin")),
  sonatypeProjectHosting := Some(GitHubHosting("toalmoal", "sbt-liquibase-plugin", "support@toalmoal.com")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/toalmoal/sbt-liquibase-plugin"),
      "scm:git@github.com:toalmoal/sbt-liquibase-plugin.git"
    )
  ),
  developers := List(
    Developer(id="ydubey", name="Yogesh Dubey", email="ydubey@toalmoal.com", url=url("https://www.toalmoal.com"))
  ),
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
  publishTo := sonatypePublishToBundle.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommand("^publishSigned"),
    releaseStepCommand("sonatypeReleaseAll"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

lazy val sbtLiquibase = Project(
  id = "sbt-liquibase",
  base = file(".")
)
.enablePlugins(ScriptedPlugin)
.settings(buildSettings)
.settings(
  libraryDependencies += "org.liquibase" % "liquibase-core" % "4.9.0"
)
