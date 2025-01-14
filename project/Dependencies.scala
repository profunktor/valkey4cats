import sbt.*

object Dependencies {
  // Versions
  object Versions {
    val valkeyGlide = "2.3.1"
    val catsCore = "2.13.0"
    val catsEffect = "3.7.0"
    val literally = "1.2.0"
    val ip4s = "3.8.0"
    val log4cats = "2.8.0"

    // Test dependencies
    val munit = "1.3.0"
    val munitCatsEffect = "2.2.0"
    val testcontainers = "2.0.5"
  }

  // Platform detection helper
  object Platform {
    lazy val os: String = sys.props("os.name").toLowerCase match {
      case mac if mac.contains("mac") => "osx"
      case linux if linux.contains("linux") => "linux"
      case win if win.contains("win") => "windows"
      case other => throw new Exception(s"Unsupported OS: $other")
    }

    lazy val arch: String = sys.props("os.arch").toLowerCase match {
      case "amd64" | "x86_64" => "x86_64"
      case "aarch64" | "arm64" => "aarch_64"
      case other => throw new Exception(s"Unsupported architecture: $other")
    }

    lazy val classifier: String = s"$os-$arch"
  }

  // Core dependencies
  val valkeyGlide = "io.valkey" % "valkey-glide" % Versions.valkeyGlide
  val valkeyGlidePlatform = (valkeyGlide classifier Platform.classifier)
    .withConfigurations(Some("compile->default"))

  val catsCore = "org.typelevel" %% "cats-core" % Versions.catsCore
  val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
  val literally = "org.typelevel" %% "literally" % Versions.literally
  val ip4s = "com.comcast" %% "ip4s-core" % Versions.ip4s

  // Logging
  val log4catsCore = "org.typelevel" %% "log4cats-core" % Versions.log4cats

  // Test dependencies
  val munit = "org.scalameta" %% "munit" % Versions.munit % Test
  val munitCatsEffect = "org.typelevel" %% "munit-cats-effect" % Versions.munitCatsEffect % Test
  val testcontainers = "org.testcontainers" % "testcontainers" % Versions.testcontainers % Test

  // Dependency groups
  object Groups {
    val core = Seq(
      valkeyGlidePlatform,
      catsCore,
      catsEffect,
      literally,
      ip4s,
    )

    val test = Seq(
      munit,
      munitCatsEffect,
      testcontainers,
    )

    val effects = Seq(
      catsEffect,
    )

    val log4cats = Seq(
      log4catsCore,
    )

    val examples = Seq(
      valkeyGlide classifier Platform.classifier,
      catsEffect,
    )
  }
}
