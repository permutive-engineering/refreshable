ThisBuild / scalaVersion           := "2.13.16"
ThisBuild / crossScalaVersions     := Seq("2.12.20", "2.13.16", "3.3.6")
ThisBuild / organization           := "com.permutive"
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible

addCommandAlias("ci-test", "fix --check; versionPolicyCheck; mdoc; publishLocal; +test")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val documentation = project
  .enablePlugins(MdocPlugin)
  .dependsOn(refreshable)

lazy val refreshable = module
  .settings(libraryDependencies += "org.typelevel" %% "cats-core" % "2.13.0")
  .settings(libraryDependencies += "org.typelevel" %% "cats-effect" % "3.5.7")
  .settings(libraryDependencies += "co.fs2" %% "fs2-core" % "3.12.0")
  .settings(libraryDependencies += "com.github.cb372" %% "cats-retry" % "3.1.3")
  .settings(libraryDependencies += "org.scalameta" %% "munit" % "1.1.1" % Test)
  .settings(libraryDependencies += "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test)
  .settings(libraryDependencies += "org.typelevel" %% "cats-effect-testkit" % "3.5.7" % Test)
