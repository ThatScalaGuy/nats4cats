lazy val V = new {
  val Scala3 = "3.3.0"
  val Scala213 = "2.13.10"
  val Cats = "2.9.0"
  val CatsEffect = "3.5.0"
  val Nats = "2.16.13"
  val Munit = "0.7.29"
  val MunitCatsEffect = "1.0.7"
  val Testcontainers = "0.40.17"
}

// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.0" // your current series x.y

ThisBuild / organization := "de.thatscalaguy"
ThisBuild / organizationName := "ThatScalaGuy"
ThisBuild / startYear := Some(2023)
ThisBuild / licenses := Seq(License.Apache2)
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
  JavaSpec.temurin("17")
)

Test / fork := true

lazy val root = (project in file(".")).aggregate(core)

lazy val core = project
  .in(file("modules/core"))
  .settings(
    name := "nats4cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % V.Cats,
      "org.typelevel" %% "cats-effect" % V.CatsEffect,
      "io.nats" % "jnats" % V.Nats,
      "org.scalameta" %% "munit" % V.Munit % Test,
      "org.typelevel" %% "munit-cats-effect-3" % V.MunitCatsEffect % Test,
      "com.dimafeng" %% "testcontainers-scala-munit" % V.Testcontainers % Test
    )
  )

lazy val docs = project.in(file("site")).enablePlugins(TypelevelSitePlugin)
