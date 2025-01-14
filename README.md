> ⚠️ **PROJECT STATUS: PRE-ALPHA / READ-ONLY**
>
> This project is under **active development**.
> The API, behavior, and structure are **unstable and will change**.
>
> Please **do not open issues or pull requests** at this time.
> Once the project reaches an initial release, contribution guidelines will be published.

valkey4cats
===========

[![CI Status](https://github.com/profunktor/valkey4cats/workflows/Scala%20CI/badge.svg)](https://github.com/profunktor/valkey4cats/actions) <a href="https://typelevel.org/cats/"><img src="https://raw.githubusercontent.com/typelevel/cats/c23130d2c2e4a320ba4cde9a7c7895c6f217d305/docs/src/main/resources/microsite/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

[Valkey](https://valkey.io) client built on top of [Cats Effect](https://typelevel.org/cats-effect/) and the async Java client [Glide](https://glide.valkey.io/).

### Quick Start

```scala
import cats.effect.*
import cats.implicits.*
import dev.profunktor.valkey4cats.Valkey
import dev.profunktor.valkey4cats.effect.Log.Stdout.given

object QuickStart extends IOApp.Simple:

  val run: IO[Unit] =
    Valkey[IO].utf8("valkey://localhost").use: valkey =>
      for
        _ <- valkey.set("foo", "123")
        x <- valkey.get("foo")
        _ <- valkey.setNx("foo", "should not happen")
        y <- valkey.get("foo")
        _ <- IO(assert(x === y))
      yield ()
```

### Dependencies

Add this to your `build.sbt` (depends on `cats-effect`):

```scala
libraryDependencies += "dev.profunktor" %% "valkey4cats-effects" % Version
```

### Log4cats support

`valkey4cats` needs a logger for internal use and provides instances for `log4cats`. It is the recommended logging library:

```scala
libraryDependencies += "dev.profunktor" %% "valkey4cats-log4cats" % Version
```

## Running the tests locally

Start both a single Valkey node and a cluster using `docker-compose`:

```bash
docker-compose up -d
sbt testAll
```

Cluster tests run automatically when port 30001 is reachable and are skipped otherwise.

## Contribution Guidelines

Going forward, features must be compatible with the [Valkey OSS project](https://valkey.io/).

## LICENSE

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with
the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
