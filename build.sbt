import com.scalapenos.sbt.prompt.SbtPrompt.autoImport.*
import com.scalapenos.sbt.prompt.*

Global / onChangedBuildSource := ReloadOnSourceChanges

// Scala version
val Scala3 = "3.8.3"
ThisBuild / scalaVersion := Scala3
ThisBuild / mimaBaseVersion := "0.1.0"
ThisBuild / organization := "dev.profunktor"
ThisBuild / homepage := Some(url("https://valkey.profunktor.dev"))
ThisBuild / developers := List(
  Developer("yisraelU", "Yisrael Union", "ysrlunion@gmail.com", url("https://github.com/yisraelU"))
)
Test / parallelExecution := false

promptTheme := PromptTheme(
  List(
    text("[sbt] ", fg(105)),
    text(_ => "valkey4cats", fg(15)).padRight(" λ ")
  )
)

// Common settings for all modules
val commonSettings = Seq(
  organizationName := "Valkey client for Cats Effect & Glide",
  startYear := Some(2018),
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  headerLicense := Some(HeaderLicense.ALv2("2018-2025", "ProfunKtor")),
  testFrameworks += new TestFramework("munit.Framework"),
  resolvers += "Apache public" at "https://repository.apache.org/content/groups/public/",
  scalacOptions ++= Seq("-Xmax-inlines", "64"),
  scalacOptions -= "-Xfatal-warnings",
  scalacOptions += "-Werror",
  Compile / doc / sources := (Compile / doc / sources).value,
  Compile / doc / scalacOptions ++= Seq("-groups", "-implicits"),
  autoAPIMappings := true,
  scalafmtOnCompile := true,
  scmInfo := Some(
    ScmInfo(url("https://github.com/profunktor/valkey4cats"), "scm:git:git@github.com:profunktor/valkey4cats.git")
  )
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  publish / skip := true
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "valkey4cats",
    publish / skip := true,
  )
  .aggregate(core, effects, log4Cats, examples)

lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(
    name := "valkey4cats-core",
    libraryDependencies ++= Dependencies.Groups.core ++ Dependencies.Groups.test,
  )

lazy val effects = project
  .in(file("modules/effects"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "valkey4cats-effects",
    libraryDependencies ++= Dependencies.Groups.effects ++ Dependencies.Groups.test
  )

lazy val log4Cats = project
  .in(file("modules/log4cats"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "valkey4cats-log4cats",
    libraryDependencies ++= Dependencies.Groups.log4cats
  )

lazy val examples = project
  .in(file("modules/examples"))
  .dependsOn(core, effects)
  .settings(commonSettings)
  .settings(
    name := "valkey4cats-examples",
    publish / skip := true,
    libraryDependencies ++= Dependencies.Groups.examples
  )

// Convenience commands
addCommandAlias("compileAll", ";core/compile ;effects/compile ;examples/compile")
addCommandAlias("testAll", ";core/test ;effects/test")
addCommandAlias("testAllQuick", ";core/testQuick ;effects/testQuick")
