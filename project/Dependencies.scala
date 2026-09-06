import sbt.*

object Dependencies {
  // Versions
  object Versions {
    val valkeyGlide = "2.5.2"
    val catsCore = "2.13.0"
    val catsEffect = "3.7.1"
    val literally = "1.2.0"
    val ip4s = "3.8.0"
    val log4cats = "2.8.0"

    // Test dependencies
    val munit = "1.3.5"
    val munitCatsEffect = "2.2.0"
    val testcontainers = "2.0.5"
  }

  // Glide uber jar (all platforms bundled); POM packaging requires explicit artifact
  val valkeyGlide = ("io.valkey" % "valkey-glide" % Versions.valkeyGlide)
    .artifacts(Artifact("valkey-glide", "jar", "jar"))

  val catsCore = "org.typelevel" %% "cats-core" % Versions.catsCore
  val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
  val literally = "org.typelevel" %% "literally" % Versions.literally
  val ip4s = "com.comcast" %% "ip4s-core" % Versions.ip4s

  // Logging
  val log4catsCore = "org.typelevel" %% "log4cats-core" % Versions.log4cats

  // Test dependencies
  val munit = "org.scalameta" %% "munit" % Versions.munit % Test
  val munitCatsEffect =
    "org.typelevel" %% "munit-cats-effect" % Versions.munitCatsEffect % Test
  val testcontainers =
    "org.testcontainers" % "testcontainers" % Versions.testcontainers % Test

  // Dependency groups
  object Groups {
    val core = Seq(
      valkeyGlide,
      catsCore,
      catsEffect,
      literally,
      ip4s
    )

    val test = Seq(
      munit,
      munitCatsEffect,
      testcontainers
    )

    val effects = Seq(
      catsEffect
    )

    val log4cats = Seq(
      log4catsCore
    )

    val examples = Seq(
      catsEffect
    )
  }
}
