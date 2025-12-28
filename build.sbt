import sbt.Keys._
import scoverage.ScoverageKeys._
import Common._

inThisBuild(
  List(
    scalaVersion       := scala3,
    organization       := "org.llm4s",
    organizationName   := "llm4s",
    versionScheme      := Some("early-semver"),
    homepage := Some(url("https://github.com/llm4s/")),
    licenses := List("MIT" -> url("https://mit-license.org/")),
    developers := List(
      Developer(
        "rorygraves",
        "Rory Graves",
        "rory.graves@fieldmark.co.uk",
        url("https://github.com/rorygraves")
      )
    ),
    ThisBuild / publishTo := {
      val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
      if (isSnapshot.value) Some("central-snapshots".at(centralSnapshots))
      else localStaging.value
    },
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toArray),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/llm4s/llm4s/"),
        "scm:git:git@github.com:llm4s/llm4s.git"
      )
    ),
    version := {
      dynverGitDescribeOutput.value match {
        case Some(out) if !out.isSnapshot() =>
          out.ref.value.stripPrefix("v")
        case Some(out) =>
          val baseVersion = out.ref.value.stripPrefix("v")
          s"$baseVersion+${out.commitSuffix.mkString("", "", "")}-SNAPSHOT"
        case None =>
          "0.0.0-UNKNOWN"
      }
    },
    ThisBuild / coverageMinimumStmtTotal := 80,
    ThisBuild / coverageFailOnMinimum    := false,
    ThisBuild / coverageHighlighting     := true,
    ThisBuild / coverageExcludedPackages := Seq(
      "org\\.llm4s\\.runner\\..*",
      "org\\.llm4s\\.samples\\..*",
      "org\\.llm4s\\.workspace\\..*"
    ).mkString(";"),
    ThisBuild / (coverageReport / aggregate) := false,
    // --- scalafix ---
    ThisBuild / scalafixDependencies += "ch.epfl.scala" %% "scalafix-rules" % "0.12.1",
    ThisBuild / scalafixOnCompile := true
  )
)

// ---- Handy aliases ----
addCommandAlias("cov", ";clean;coverage;test;coverageAggregate;coverageReport;coverageOff")
addCommandAlias("covReport", ";clean;coverage;test;coverageReport;coverageOff")
addCommandAlias("buildAll", ";clean;compile;test")
addCommandAlias("publishAll", ";clean;publish")
addCommandAlias("testAll", ";test")
addCommandAlias("cleanTestAll", ";clean;test")
addCommandAlias("cleanTestAllAndFormat", ";scalafmtAll;clean;test")
addCommandAlias("compileAll", ";compile")



// ---- shared settings ----
lazy val commonSettings = Seq(
  Compile / scalacOptions := scalacOptionsForVersion(scalaVersion.value),
  Test / scalacOptions    := scalacOptionsForVersion(scalaVersion.value),
  // Suppress ScalaDoc warnings from third-party libraries (e.g., ScalaTest)
  Compile / doc / scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq("-Wconf:cat=scaladoc:silent")
      case _ => Seq.empty
    }
  },
  semanticdbEnabled       := CrossVersion.partialVersion(scalaVersion.value).exists(_._1 == 3),
  Test / scalafix / unmanagedSources := Seq.empty,
  Compile / packageDoc / publishArtifact := !isSnapshot.value,
  libraryDependencies ++= Seq(
    Deps.cats,
    Deps.upickle,
    Deps.logback,
    Deps.monocleCore,
    Deps.monocleMacro,
    Deps.scalatest % Test,
    Deps.scalamock % Test,
    Deps.fansi,
    Deps.postgres,
    Deps.sqlite,
    Deps.config,
    Deps.pureConfig,
    Deps.hikariCP
  )
)

// ---- projects ----
lazy val llm4s = (project in file("."))
  .aggregate(core, samples, workspaceShared, workspaceRunner, workspaceClient, workspaceSamples)

lazy val core = (project in file("modules/core"))
  .settings(
    name := "core",
    commonSettings,
    Test / fork := true,
    Compile / mainClass := None,
    Compile / discoveredMainClasses := Seq.empty,
    resolvers += "Vosk Repository" at "https://alphacephei.com/maven/",
    libraryDependencies ++= Seq(
      Deps.azureOpenAI,
      Deps.anthropic,
      Deps.jtokkit,
      Deps.requests,
      Deps.websocket,
      Deps.scalatest % Test,
      Deps.scalamock % Test,
      Deps.sttp,
      Deps.ujson,
      Deps.pdfbox,
      Deps.commonsIO,
      Deps.tika,
      Deps.poi,
      Deps.requests,
      Deps.jsoup,
      Deps.jna,
      Deps.vosk,
      Deps.postgres,
      Deps.config,
      Deps.hikariCP
    )
  )

lazy val workspaceShared = (project in file("modules/workspace/workspaceShared"))
  .settings(
    name := "workspaceShared",
    commonSettings,
    Compile / discoveredMainClasses := Seq.empty,
    coverageEnabled := false
  )

lazy val workspaceClient = (project in file("modules/workspace/workspaceClient"))
  .dependsOn(workspaceShared, core)
  .settings(
    name := "workspaceShared",
    commonSettings,
    Compile / discoveredMainClasses := Seq.empty,
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      Deps.azureOpenAI,
      Deps.anthropic,
      Deps.jtokkit,
      Deps.requests,
      Deps.websocket,
      Deps.scalatest % Test,
      Deps.scalamock % Test,
      Deps.sttp,
      Deps.ujson,
      Deps.pdfbox,
      Deps.commonsIO,
      Deps.tika,
      Deps.poi,
      Deps.requests,
      Deps.jsoup,
      Deps.jna,
      Deps.vosk,
      Deps.postgres,
      Deps.config,
      Deps.hikariCP
    )
  )

lazy val workspaceRunner = (project in file("modules/workspace/workspaceRunner"))
  .dependsOn(workspaceShared)
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    name := "workspaceRunner",
    commonSettings,
    Compile / mainClass := Some("org.llm4s.runner.RunnerMain"),
    libraryDependencies ++= Seq(
      Deps.cask,
      Deps.requests,
      Deps.postgres,
      Deps.config,
      Deps.hikariCP
    ),
    publish / skip := true,
    coverageEnabled := false
  )
  .settings(WorkspaceRunnerDocker.settings)

lazy val samples = (project in file("modules//samples"))
  .dependsOn(core)
  .settings(
    name := "samples",
    commonSettings,
    publish / skip := true,
    coverageEnabled := false
  )

lazy val workspaceSamples = (project in file("modules/workspace/workspaceSamples"))
  .dependsOn(workspaceShared, workspaceRunner, workspaceClient, samples)
  .settings(
    name := "workspaceSamples",
    commonSettings,
    publish / skip := true,
    coverageEnabled := false
  )

mimaPreviousArtifacts := Set(
  organization.value %% "llm4s" % "0.1.4"
)
