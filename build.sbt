ThisBuild / scalaVersion           := "2.13.12"
ThisBuild / crossScalaVersions     := Seq("2.12.18", "2.13.12", "3.3.3")
ThisBuild / organization           := "com.permutive"
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible

addCommandAlias("ci-test", "fix --check; versionPolicyCheck; mdoc; publishLocal; +test")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val documentation = project
  .enablePlugins(MdocPlugin)
  .dependsOn(refreshable)

lazy val refreshable = module
  .settings(libraryDependencies += "org.typelevel" %% "cats-core" % "2.10.0")
  .settings(libraryDependencies += "org.typelevel" %% "cats-effect" % "3.5.4")
  .settings(libraryDependencies += "co.fs2" %% "fs2-core" % "3.10.2")
  .settings(libraryDependencies += "com.github.cb372" %% "cats-retry" % "3.1.3")
  .settings(libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test)
  .settings(libraryDependencies += "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test)
  .settings(libraryDependencies += "org.typelevel" %% "cats-effect-testkit" % "3.5.4" % Test)
