lazy val V = new {
  val Scala3          = "3.3.3"
  val Scala213        = "2.13.10"
  val Cats            = "2.12.0"
  val CatsEffect      = "3.5.4"
  val Nats            = "2.19.1"
  val Munit           = "1.0.0"
  val MunitCatsEffect = "2.0.0"
  val Testcontainers  = "0.41.4"
  val Circe           = "0.14.9"
  val Otel4s          = "0.8.1"
}

// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.4" // your current series x.y

ThisBuild / organization     := "de.thatscalaguy"
ThisBuild / organizationName := "ThatScalaGuy"
ThisBuild / startYear        := Some(2023)
ThisBuild / licenses         := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("ThatScalaGuy", "Sven Herrmann")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

//ThisBuild / crossScalaVersions := Seq(V.Scala3, V.Scala213)
ThisBuild / scalaVersion := V.Scala3 // the default Scala

ThisBuild / githubWorkflowJavaVersions := Seq(
  JavaSpec.temurin("8"),
  JavaSpec.temurin("11"),
  JavaSpec.temurin("17"),
  JavaSpec.temurin("21")
)

Test / fork                        := true
Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat

lazy val root = (project in file("."))
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, circe, service, examples)

lazy val core = project
  .in(file("modules/core"))
  .settings(
    name := "nats4cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"                  % V.Cats,
      "org.typelevel" %% "cats-effect"                % V.CatsEffect,
      "io.nats"        % "jnats"                      % V.Nats,
      "org.scalameta" %% "munit"                      % V.Munit           % Test,
      "org.typelevel" %% "munit-cats-effect"          % V.MunitCatsEffect % Test,
      "com.dimafeng"  %% "testcontainers-scala-munit" % V.Testcontainers  % Test
    )
  )

lazy val circe = project
  .in(file("modules/circe"))
  .settings(
    name := "nats4cats-circe",
    libraryDependencies ++= Seq(
      "io.circe"      %% "circe-core"                 % V.Circe,
      "io.circe"      %% "circe-parser"               % V.Circe,
      "org.scalameta" %% "munit"                      % V.Munit           % Test,
      "org.typelevel" %% "munit-cats-effect"          % V.MunitCatsEffect % Test,
      "com.dimafeng"  %% "testcontainers-scala-munit" % V.Testcontainers  % Test
    )
  )
  .dependsOn(core)

lazy val service = project
  .in(file("modules/service"))
  .settings(
    name := "nats4cats-service",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "otel4s-oteljava" % V.Otel4s
    )
  )
  .dependsOn(core)

lazy val examples = project
  .in(file("modules/examples"))
  .enablePlugins(NoPublishPlugin)
  .settings(
    name := "nats4cats-examples",
    libraryDependencies ++= Seq(
      "io.opentelemetry" % "opentelemetry-exporter-otlp"               % "1.40.0" % Runtime,
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.40.0" % Runtime
    ),
    javaOptions += "-Dotel.java.global-autoconfigure.enabled=true",
    javaOptions += "-Dotel.service.name=example-service",
    javaOptions += "-Dotel.metrics.exporter=none"
  )
  .dependsOn(core, service)

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(tlSiteHelium ~= {
    import laika.helium.config._
    import laika.ast.Path.Root
    _.site
      .topNavigationBar(
        homeLink = IconLink.internal(Root / "index.md", HeliumIcon.home)
      )
  })
