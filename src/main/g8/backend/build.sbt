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
        library.akkaHttpSprayJson,
        library.fs,
        library.loraControlPlane,
        library.loraStreams,
        library.ioxSss,
        library.sprayJson,
        library.streambedCore,
        library.streambedDurableQueueRemoteClient,
        library.streambedHttp,
        library.akkaTestkit      % Test,
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
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "frontend" / "dist"
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val akka       = "2.5.23"
      val akkaHttp   = "10.1.8"
      val scalaCheck = "1.14.0"
      val sprayJson  = "1.3.5"
      val streambed  = "0.41.1"
      val utest      = "0.6.9"
    }
    val akkaHttpSprayJson                 = "com.typesafe.akka"        %% "akka-http-spray-json"                  % Version.akkaHttp
    val akkaTestkit                       = "com.typesafe.akka"        %% "akka-testkit"                          % Version.akka
    val fs                                = "com.cisco.streambed"      %% "fs"                                    % Version.streambed
    val loraControlPlane                  = "com.cisco.streambed"      %% "lora-control-plane"                    % Version.streambed
    val loraStreams                       = "com.cisco.streambed"      %% "lora-streams"                          % Version.streambed
    val ioxSss                            = "com.cisco.streambed"      %% "iox-sss"                               % Version.streambed
    val sprayJson                         = "io.spray"                 %% "spray-json"                            % Version.sprayJson
    val streambedCore                     = "com.cisco.streambed"      %% "streambed-core"                        % Version.streambed
    val streambedDurableQueueRemoteClient = "com.cisco.streambed"      %% "streambed-durable-queue-remote-client" % Version.streambed
    val streambedHttp                     = "com.cisco.streambed"      %% "streambed-http"                        % Version.streambed
    val streambedTestKit                  = "com.cisco.streambed"      %% "streambed-testkit"                     % Version.streambed
    val utest                             = "com.lihaoyi"              %% "utest"                                 % Version.utest
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  Seq(
    scalaVersion := "2.12.9",
    organization := "$organization;format="package"$",
    organizationName := "$organizationName$",
    startYear := Some(2018),
    headerLicense := Some(HeaderLicense.Custom("Copyright (c) $organizationName$, 2019")),
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
