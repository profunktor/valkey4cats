ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

addSbtPlugin("com.typesafe"      % "sbt-mima-plugin" % "1.1.4")
addSbtPlugin("com.github.sbt"    % "sbt-ci-release"  % "1.6.0")
addSbtPlugin("org.typelevel"     % "sbt-tpolecat"    % "0.5.2")
addSbtPlugin("de.heikoseeberger" % "sbt-header"      % "5.10.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"    % "2.5.2")
addSbtPlugin("com.47deg"         % "sbt-microsites"  % "1.4.4")
addSbtPlugin("org.scalameta"     % "sbt-mdoc"        % "2.5.4")
addSbtPlugin("com.github.sbt"    % "sbt-site"        % "1.7.0")
addSbtPlugin("com.github.sbt"    % "sbt-unidoc"      % "0.5.0")
