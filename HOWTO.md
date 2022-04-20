## Create SBT creadentials file $HOME/.sbt/(sbt-version 0.13 or 1.0)/sonatype.sbt
credentials += Credentials("Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    "(Sonatype user name)",
    "(Sonatype password)"
)

##
$ sbt publishSigned

$ sbt sonatypeBundleRelease