package dev.profunktor.valkey4cats.examples

import cats.effect.*
import dev.profunktor.valkey4cats.effect.Log
import dev.profunktor.valkey4cats.syntax.literals.* // Import the valkey string interpolator

/** Example demonstrating compile-time validated Valkey URI literals
  *
  * The valkey"..." interpolator validates URIs at compile time, catching
  * errors before runtime.
  */
object LiteralsExample extends IOApp.Simple {

  implicit val logger: Log[IO] = Log.Stdout.instance[IO]

  def run: IO[Unit] = {
    // These URIs are validated at compile time!
    val localUri = valkey"redis://localhost:6379"
    val secureUri = valkey"rediss://secure-server:6380"
    val withAuth = valkey"redis://:mypassword@localhost:6379"
    val withDb = valkey"redis://localhost:6379/2"

    IO.println("=== Compile-Time Validated URI Literals ===") *>
      IO.println(s"Local URI: $localUri") *>
      IO.println(s"Secure URI: $secureUri") *>
      IO.println(s"With Auth: $withAuth") *>
      IO.println(s"With DB: $withDb") *>
      IO.println("\nAll URIs validated at compile time!")

    // Try uncommenting this line - it will fail at compile time!
    // val invalid = valkey"not-a-valid-uri"
    // val badScheme = valkey"http://localhost:6379"
  }
}
