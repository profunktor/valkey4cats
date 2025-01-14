package dev.profunktor.valkey4cats.examples

import cats.effect.*
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.model.ValkeyClusterConfig

/** Example demonstrating cluster URI validation
  *
  * Shows how ValkeyClusterConfig.fromUris validates that all URIs have
  * consistent settings (TLS, credentials, protocol).
  */
object ClusterValidationExample extends IOApp.Simple {

  implicit val logger: Log[IO] = Log.Stdout.instance[IO]

  def run: IO[Unit] = {
    IO.println("=== Cluster URI Validation Examples ===\n") *>
      validExample *>
      IO.println("\n") *>
      invalidTlsExample *>
      IO.println("\n") *>
      invalidCredsExample
  }

  /** Example 1: Valid - all URIs have consistent settings */
  def validExample: IO[Unit] = {
    IO.println("Example 1: Valid cluster URIs (all use redis://)") *>
      ValkeyClusterConfig
        .fromUris[IO](
          List(
            "redis://localhost:7000",
            "redis://localhost:7001",
            "redis://localhost:7002"
          )
        )
        .attempt
        .flatMap {
          case Right(config) =>
            IO.println(
              s"✓ Success! Created cluster config with ${config.addresses.size} seed nodes"
            ) *>
              IO.println(s"  TLS enabled: ${config.tlsMode.isEnabled}")
          case Left(error) =>
            IO.println(s"✗ Unexpected error: ${error.getMessage}")
        }
  }

  /** Example 2: Invalid - mixed TLS settings */
  def invalidTlsExample: IO[Unit] = {
    IO.println("Example 2: Invalid - mixed TLS (redis:// and rediss://)") *>
      ValkeyClusterConfig
        .fromUris[IO](
          List(
            "redis://localhost:7000", // No TLS
            "rediss://localhost:7001", // TLS!
            "redis://localhost:7002" // No TLS
          )
        )
        .attempt
        .flatMap {
          case Right(_) =>
            IO.println("✗ Unexpected success - should have failed!")
          case Left(error) =>
            IO.println(s"✓ Correctly rejected:") *>
              IO.println(s"  ${error.getMessage}")
        }
  }

  /** Example 3: Invalid - inconsistent credentials */
  def invalidCredsExample: IO[Unit] = {
    IO.println("Example 3: Invalid - inconsistent credentials") *>
      ValkeyClusterConfig
        .fromUris[IO](
          List(
            "redis://:password1@localhost:7000",
            "redis://:password2@localhost:7001", // Different password!
            "redis://:password1@localhost:7002"
          )
        )
        .attempt
        .flatMap {
          case Right(_) =>
            IO.println("✗ Unexpected success - should have failed!")
          case Left(error) =>
            IO.println(s"✓ Correctly rejected:") *>
              IO.println(s"  ${error.getMessage}")
        }
  }
}
