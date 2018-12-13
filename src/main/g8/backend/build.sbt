// *****************************************************************************
// Projects
// *****************************************************************************

import com.typesafe.sbt.packager.docker._

lazy val root =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin, DockerPlugin, GitVersioning, GitBranchPrompt)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.fs,
        library.loraControlPlane,
        library.loraStreams,
        library.ioxSss,
        library.jaegerTracing,
        library.sprayJson,
        library.streambedCore,
        library.streambedDurableQueueRemoteClient,
        library.streambedHttp,
        library.streambedIdentity,
        library.akkaTestkit      % Test,
        library.scalaCheck       % Test,
        library.streambedTestKit % Test,
        library.utest            % Test
      ),
      // #
      Seq(
        mappings in Docker := assembly.value.pair(Path.flatRebase("/opt/docker/lib")),
        dockerCommands := Seq(
          Cmd("FROM", "scratch"),
          Cmd("COPY", "opt/docker", "/opt/docker")
        )
      ),
      name := "$name;format="norm"$",
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "frontend"
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val akka       = "2.5.19"
      val loraSdk    = "0.10.1"
      val scalaCheck = "1.14.0"
      val sprayJson  = "1.3.4"
      val streambed  = "0.24.4"
      val utest      = "0.6.4"
    }
    val akkaTestkit                       = "com.typesafe.akka"        %% "akka-testkit"       % Version.akka
    val fs                                = "com.cisco.streambed"      %% "fs"                 % Version.streambed
    val loraControlPlane                  = "com.cisco.streambed.lora" %% "lora-control-plane" % Version.loraSdk
    val loraStreams                       = "com.cisco.streambed.lora" %% "lora-streams"       % Version.loraSdk
    val ioxSss                            = "com.cisco.streambed"      %% "iox-sss"            % Version.streambed
    val jaegerTracing                     = "com.cisco.streambed"      %% "jaeger-tracing"     % Version.streambed
    val scalaCheck                        = "org.scalacheck"           %% "scalacheck"         % Version.scalaCheck
    val sprayJson                         = "io.spray"                 %% "spray-json"         % Version.sprayJson
    val streambedCore                     = "com.cisco.streambed"      %% "streambed-core"     % Version.streambed
    val streambedDurableQueueRemoteClient = "com.cisco.streambed"      %% "streambed-durable-queue-remote-client" % Version.streambed
    val streambedHttp                     = "com.cisco.streambed"      %% "streambed-http"                        % Version.streambed
    val streambedIdentity                 = "com.cisco.streambed"      %% "streambed-identity"                    % Version.streambed
    val streambedTestKit                  = "com.cisco.streambed"      %% "streambed-testkit"                     % Version.streambed
    val utest                             = "com.lihaoyi"              %% "utest"                                 % Version.utest
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  Seq(
    scalaVersion := "2.12.7",
    organization := "$organization;format="package"$",
    organizationName := "$organizationName$",
    startYear := Some(2018),
    headerLicense := Some(HeaderLicense.Custom("Copyright (c) $organizationName$, 2018")),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8",
      "-Ypartial-unification",
      "-Ywarn-unused-import"
    ),
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    Test / unmanagedSourceDirectories := Seq((Test / scalaSource).value),
    publishArtifact in (Compile, packageDoc) := false,  // Remove if these libraries are OSS
    publishArtifact in (Compile, packageSrc) := false,  // Remove if these libraries are OSS
    testFrameworks += new TestFramework("utest.runner.Framework"),
    wartremoverWarnings in (Compile, compile) ++= Warts.unsafe,
    resolvers += "streambed-repositories" at "https://repositories.streambed.io/jars/",

    git.useGitDescribe := true,

    scalafmtOnCompile := true
  )
