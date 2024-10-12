## Login to https://s01.oss.sonatype.org to generate user token (if not already exists)

## Create SBT creadentials file $HOME/.sbt/(sbt-version 0.13 or 1.0)/sonatype.sbt
credentials += Credentials("Sonatype Nexus Repository Manager",
    "s01.oss.sonatype.org",
    "(Sonatype generated user token user name)",
    "(Sonatype generated user token  password)"
)

##
$ sbt publishSigned

$ sbt sonatypeBundleRelease