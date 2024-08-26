import com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit
import org.scalajs.jsenv.nodejs.*
import org.typelevel.scalacoptions.ScalacOption
import org.typelevel.scalacoptions.ScalacOptions
import org.typelevel.scalacoptions.ScalaVersion

val scala3Version   = "3.5.0"
val scala212Version = "2.12.19"
val scala213Version = "2.13.14"

val zioVersion       = "2.1.7"
val scalaTestVersion = "3.2.19"

val compilerOptions = Set(
    ScalacOptions.encoding("utf8"),
    ScalacOptions.feature,
    ScalacOptions.unchecked,
    ScalacOptions.deprecation,
    ScalacOptions.warnValueDiscard,
    ScalacOptions.languageStrictEquality,
    ScalacOptions.release("11"),
    ScalacOptions.advancedKindProjector
)

ThisBuild / scalaVersion := scala3Version
publish / skip           := true

ThisBuild / organization := "io.getkyo"
ThisBuild / homepage     := Some(url("https://getkyo.io"))
ThisBuild / licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / developers := List(
    Developer(
        "fwbrasil",
        "Flavio Brasil",
        "fwbrasil@gmail.com",
        url("https://github.com/fwbrasil/")
    )
)

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository     := "https://s01.oss.sonatype.org/service/local"
ThisBuild / sonatypeProfileName    := "io.getkyo"

ThisBuild / useConsoleForROGit := (baseDirectory.value / ".git").isFile

lazy val `kyo-settings` = Seq(
    fork               := true,
    scalaVersion       := scala3Version,
    crossScalaVersions := List(scala3Version),
    scalacOptions ++= scalacOptionTokens(compilerOptions).value,
    scalafmtOnCompile := true,
    Test / testOptions += Tests.Argument("-oDG"),
    ThisBuild / versionScheme               := Some("early-semver"),
    libraryDependencies += "org.scalatest" %%% "scalatest" % scalaTestVersion % Test,
    Test / javaOptions += "--add-opens=java.base/java.lang=ALL-UNNAMED"
)

lazy val kyoJVM = project
    .in(file("."))
    .settings(
        name := "kyoJVM",
        `kyo-settings`
    )
    .aggregate(
        `kyo-scheduler`.jvm,
        `kyo-scheduler-zio`.jvm,
        `kyo-tag`.jvm,
        `kyo-data`.jvm,
        `kyo-prelude`.jvm,
        `kyo-core`.jvm,
        `kyo-direct`.jvm,
        `kyo-stats-registry`.jvm,
        `kyo-stats-otel`.jvm,
        `kyo-cache`.jvm,
        `kyo-sttp`.jvm,
        `kyo-tapir`.jvm,
        `kyo-caliban`.jvm,
        `kyo-bench`.jvm,
        `kyo-test`.jvm,
        `kyo-zio`.jvm,
        `kyo-combinators`.jvm,
        `kyo-examples`.jvm
    )

lazy val kyoJS = project
    .in(file("js"))
    .settings(
        name := "kyoJS",
        `kyo-settings`
    )
    .aggregate(
        `kyo-scheduler`.js,
        `kyo-tag`.js,
        `kyo-data`.js,
        `kyo-prelude`.js,
        `kyo-core`.js,
        `kyo-direct`.js,
        `kyo-stats-registry`.js,
        `kyo-sttp`.js,
        `kyo-test`.js,
        `kyo-zio`.js,
        `kyo-combinators`.js
    )

lazy val `kyo-scheduler` =
    crossProject(JSPlatform, JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .dependsOn(`kyo-stats-registry`)
        .in(file("kyo-scheduler"))
        .settings(
            `kyo-settings`,
            scalacOptions ++= scalacOptionToken(ScalacOptions.source3).value,
            crossScalaVersions                      := List(scala3Version, scala212Version, scala213Version),
            libraryDependencies += "org.scalatest" %%% "scalatest"       % scalaTestVersion % Test,
            libraryDependencies += "ch.qos.logback"  % "logback-classic" % "1.5.7"          % Test
        )
        .jsSettings(
            `js-settings`,
            libraryDependencies += "org.scala-js" %%% "scala-js-macrotask-executor" % "1.1.1"
        )

lazy val `kyo-scheduler-zio` = sbtcrossproject.CrossProject("kyo-scheduler-zio", file("kyo-scheduler-zio"))(JVMPlatform)
    .withoutSuffixFor(JVMPlatform)
    .crossType(CrossType.Full)
    .dependsOn(`kyo-scheduler`)
    .settings(
        `kyo-settings`,
        libraryDependencies += "dev.zio"       %%% "zio"       % zioVersion,
        libraryDependencies += "org.scalatest" %%% "scalatest" % scalaTestVersion % Test
    )
    .settings(
        scalacOptions ++= scalacOptionToken(ScalacOptions.source3).value,
        crossScalaVersions := List(scala3Version, scala212Version, scala213Version)
    )

lazy val `kyo-tag` =
    crossProject(JSPlatform, JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-tag"))
        .settings(
            `kyo-settings`,
            libraryDependencies += "org.scalatest" %%% "scalatest"     % scalaTestVersion % Test,
            libraryDependencies += "dev.zio"       %%% "izumi-reflect" % "2.3.10"         % Test
        )
        .jsSettings(`js-settings`)

lazy val `kyo-data` =
    crossProject(JSPlatform, JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .dependsOn(`kyo-tag`)
        .in(file("kyo-data"))
        .settings(
            `kyo-settings`,
            libraryDependencies += "dev.zio" %%% "zio-test-sbt" % "2.1.2" % Test
        )
        .jsSettings(`js-settings`)

lazy val `kyo-prelude` =
    crossProject(JSPlatform, JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .dependsOn(`kyo-data`)
        .in(file("kyo-prelude"))
        .settings(
            `kyo-settings`,
            libraryDependencies += "com.lihaoyi" %%% "pprint"        % "0.9.0",
            libraryDependencies += "org.jctools"   % "jctools-core"  % "4.0.5",
            libraryDependencies += "dev.zio"     %%% "zio-laws-laws" % "1.0.0-RC27" % Test,
            libraryDependencies += "dev.zio"     %%% "zio-test-sbt"  % "2.1.2"      % Test,
            libraryDependencies += "org.javassist" % "javassist"     % "3.30.2-GA"  % Test
        )
        .jsSettings(`js-settings`)

lazy val `kyo-core` =
    crossProject(JSPlatform, JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .dependsOn(`kyo-scheduler`)
        .dependsOn(`kyo-prelude`)
        .in(file("kyo-core"))
        .settings(
            `kyo-settings`,
            libraryDependencies += "org.jctools"    % "jctools-core"    % "4.0.5",
            libraryDependencies += "org.slf4j"      % "slf4j-api"       % "2.0.16",
            libraryDependencies += "dev.zio"      %%% "zio-laws-laws"   % "1.0.0-RC27" % Test,
            libraryDependencies += "dev.zio"      %%% "zio-test-sbt"    % "2.1.7"      % Test,
            libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.7"      % Test,
            libraryDependencies += "org.javassist"  % "javassist"       % "3.30.2-GA"  % Test
        )
        .jsSettings(`js-settings`)

lazy val `kyo-direct` =
    crossProject(JSPlatform, JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-direct"))
        .dependsOn(`kyo-core`)
        .settings(
            `kyo-settings`,
            libraryDependencies += "com.github.rssh" %%% "dotty-cps-async" % "0.9.21"
        )
        .jsSettings(`js-settings`)

lazy val `kyo-stats-registry` =
    crossProject(JSPlatform, JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-stats-registry"))
        .settings(
            `kyo-settings`,
            scalacOptions ++= scalacOptionToken(ScalacOptions.source3).value,
            libraryDependencies += "org.hdrhistogram" % "HdrHistogram" % "2.2.2",
            libraryDependencies += "org.scalatest"  %%% "scalatest"    % scalaTestVersion % Test,
            crossScalaVersions                       := List(scala3Version, scala212Version, scala213Version)
        )
        .jsSettings(`js-settings`)

lazy val `kyo-stats-otel` =
    crossProject(JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-stats-otel"))
        .dependsOn(`kyo-core`)
        .settings(
            `kyo-settings`,
            libraryDependencies += "io.opentelemetry" % "opentelemetry-api"                % "1.41.0",
            libraryDependencies += "io.opentelemetry" % "opentelemetry-sdk"                % "1.41.0" % Test,
            libraryDependencies += "io.opentelemetry" % "opentelemetry-exporters-inmemory" % "0.9.1"  % Test
        )

lazy val `kyo-cache` =
    crossProject(JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-cache"))
        .dependsOn(`kyo-core`)
        .settings(
            `kyo-settings`,
            libraryDependencies += "com.github.ben-manes.caffeine" % "caffeine" % "3.1.8"
        )

lazy val `kyo-sttp` =
    crossProject(JSPlatform, JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-sttp"))
        .dependsOn(`kyo-core`)
        .settings(
            `kyo-settings`,
            libraryDependencies += "com.softwaremill.sttp.client3" %%% "core" % "3.9.8"
        )
        .jsSettings(`js-settings`)

lazy val `kyo-tapir` =
    crossProject(JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-tapir"))
        .dependsOn(`kyo-core`)
        .dependsOn(`kyo-sttp`)
        .settings(
            `kyo-settings`,
            libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-core"         % "1.11.1",
            libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % "1.11.1"
        )

lazy val `kyo-caliban` =
    crossProject(JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Pure)
        .in(file("kyo-caliban"))
        .dependsOn(`kyo-core`)
        .dependsOn(`kyo-tapir`)
        .dependsOn(`kyo-zio`)
        .dependsOn(`kyo-sttp`)
        .settings(
            `kyo-settings`,
            libraryDependencies += "com.github.ghostdogpr"       %% "caliban"        % "2.8.1",
            libraryDependencies += "com.github.ghostdogpr"       %% "caliban-tapir"  % "2.8.1",
            libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % "1.11.1" % Test
        )

lazy val `kyo-test` =
    crossProject(JVMPlatform, JSPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-test"))
        .dependsOn(`kyo-core`)
        .dependsOn(`kyo-zio`)
        .settings(
            `kyo-settings`,
            libraryDependencies += "dev.zio" %%% "zio"          % zioVersion,
            libraryDependencies += "dev.zio" %%% "zio-test"     % zioVersion,
            libraryDependencies += "dev.zio" %%% "zio-test-sbt" % zioVersion % Test
        ).jsSettings(
            `js-settings`
        )

lazy val `kyo-zio` =
    crossProject(JVMPlatform, JSPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-zio"))
        .dependsOn(`kyo-core`)
        .settings(
            `kyo-settings`,
            libraryDependencies += "dev.zio" %%% "zio"          % zioVersion,
            libraryDependencies += "dev.zio" %%% "zio-test"     % zioVersion,
            libraryDependencies += "dev.zio" %%% "zio-test-sbt" % zioVersion % Test
        ).jsSettings(
            `js-settings`
        )

lazy val `kyo-combinators` =
    crossProject(JSPlatform, JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-combinators"))
        .dependsOn(`kyo-core`)
        .settings(
            `kyo-settings`
        )
        .jsSettings(`js-settings`)

lazy val `kyo-examples` =
    crossProject(JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("kyo-examples"))
        .dependsOn(`kyo-tapir`)
        .dependsOn(`kyo-direct`)
        .dependsOn(`kyo-core`)
        .settings(
            `kyo-settings`,
            fork := true,
            javaOptions ++= Seq(
                "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"
            ),
            Compile / doc / sources                              := Seq.empty,
            libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % "1.10.15"
        )

lazy val `kyo-bench` =
    crossProject(JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Pure)
        .in(file("kyo-bench"))
        .enablePlugins(JmhPlugin)
        .dependsOn(`kyo-core`)
        .dependsOn(`kyo-sttp`)
        .dependsOn(`kyo-scheduler-zio`)
        .settings(
            `kyo-settings`,
            Test / testForkedParallel := true,
            // Forks each test suite individually
            Test / testGrouping := {
                val javaOptionsValue = javaOptions.value.toVector
                val envsVarsValue    = envVars.value
                (Test / definedTests).value map { test =>
                    Tests.Group(
                        name = test.name,
                        tests = Seq(test),
                        runPolicy = Tests.SubProcess(
                            ForkOptions(
                                javaHome = javaHome.value,
                                outputStrategy = outputStrategy.value,
                                bootJars = Vector.empty,
                                workingDirectory = Some(baseDirectory.value),
                                runJVMOptions = javaOptionsValue,
                                connectInput = connectInput.value,
                                envVars = envsVarsValue
                            )
                        )
                    )
                }
            },
            libraryDependencies += "dev.zio"             %% "izumi-reflect"       % "2.3.10",
            libraryDependencies += "org.typelevel"       %% "cats-effect"         % "3.5.4",
            libraryDependencies += "org.typelevel"       %% "log4cats-core"       % "2.7.0",
            libraryDependencies += "org.typelevel"       %% "log4cats-slf4j"      % "2.7.0",
            libraryDependencies += "org.typelevel"       %% "cats-mtl"            % "1.5.0",
            libraryDependencies += "dev.zio"             %% "zio-logging"         % "2.3.1",
            libraryDependencies += "dev.zio"             %% "zio-logging-slf4j2"  % "2.3.1",
            libraryDependencies += "dev.zio"             %% "zio"                 % zioVersion,
            libraryDependencies += "dev.zio"             %% "zio-concurrent"      % zioVersion,
            libraryDependencies += "dev.zio"             %% "zio-prelude"         % "1.0.0-RC27",
            libraryDependencies += "com.softwaremill.ox" %% "core"                % "0.0.25",
            libraryDependencies += "co.fs2"              %% "fs2-core"            % "3.10.2",
            libraryDependencies += "org.http4s"          %% "http4s-ember-client" % "0.23.27",
            libraryDependencies += "org.http4s"          %% "http4s-dsl"          % "0.23.27",
            libraryDependencies += "dev.zio"             %% "zio-http"            % "3.0.0-RC9",
            libraryDependencies += "io.vertx"             % "vertx-core"          % "4.5.9",
            libraryDependencies += "io.vertx"             % "vertx-web"           % "4.5.9",
            libraryDependencies += "org.scalatest"       %% "scalatest"           % scalaTestVersion % Test
        )

lazy val rewriteReadmeFile = taskKey[Unit]("Rewrite README file")

addCommandAlias("checkReadme", ";readme/rewriteReadmeFile; readme/mdoc")

lazy val readme =
    crossProject(JVMPlatform)
        .withoutSuffixFor(JVMPlatform)
        .crossType(CrossType.Full)
        .in(file("target/readme"))
        .enablePlugins(MdocPlugin)
        .settings(
            `kyo-settings`,
            mdocIn  := new File("./../../README-in.md"),
            mdocOut := new File("./../../README-out.md"),
            rewriteReadmeFile := {
                val readmeFile       = new File("README.md")
                val targetReadmeFile = new File("target/README-in.md")
                val contents         = IO.read(readmeFile)
                val newContents      = contents.replaceAll("```scala\n", "```scala mdoc:reset\n")
                IO.write(targetReadmeFile, newContents)
            }
        )
        .dependsOn(
            `kyo-core`,
            `kyo-direct`,
            `kyo-cache`,
            `kyo-sttp`,
            `kyo-tapir`,
            `kyo-bench`,
            `kyo-zio`,
            `kyo-caliban`,
            `kyo-combinators`
        )
        .settings(
            libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % "1.10.7"
        )

lazy val `js-settings` = Seq(
    Compile / doc / sources                     := Seq.empty,
    fork                                        := false,
    jsEnv                                       := new NodeJSEnv(NodeJSEnv.Config().withArgs(List("--max_old_space_size=5120"))),
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.5.0" % "provided"
)

def scalacOptionToken(proposedScalacOption: ScalacOption) =
    scalacOptionTokens(Set(proposedScalacOption))

def scalacOptionTokens(proposedScalacOptions: Set[ScalacOption]) = Def.setting {
    val version = ScalaVersion.fromString(scalaVersion.value).right.get
    ScalacOptions.tokensForVersion(version, proposedScalacOptions)
}
