import scalariform.formatter.preferences.{CompactControlReadability, DoubleIndentClassDeclaration, PreserveSpaceBeforeArguments, SpacesAroundMultiImports}
import de.heikoseeberger.sbtheader.HeaderPattern

name := "surrogate-service"

val akkaVersion = "2.4.11"
val kafkaVersion = "0.10.0.1"

val kafkaClients = "org.apache.kafka" % "kafka-clients" % kafkaVersion

val commonDependencies = Seq(
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test",
    "com.typesafe.akka" % "akka-stream-kafka_2.11" % "0.13"
)

val coreDependencies = Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    kafkaClients,
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "org.reactivestreams" % "reactive-streams-tck" % "1.0.0" % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "junit" % "junit" % "4.12" % "test",
    "ch.qos.logback" % "logback-classic" % "1.1.3" % "test",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.12" % "test",
    "org.mockito" % "mockito-core" % "1.10.19" % "test",
    "net.manub" %% "scalatest-embedded-kafka" % "0.7.1" % "test",
    "org.iq80.leveldb" % "leveldb" % "0.7",
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
    "com.typesafe" % "config" % "1.3.0",
    "commons-io" % "commons-io" % "2.4" % "test"
)

val commonSettings =
    scalariformSettings ++ Seq(
        organization := "personal",
        organizationName := "greg_huang",
        startYear := Some(2017),
        test in assembly := {},
        licenses := Seq("Apache License 2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
        scalaVersion := "2.11.2",
        crossVersion := CrossVersion.binary,
        scalacOptions ++= Seq(
            "-deprecation",
            "-encoding", "UTF-8", // yes, this is 2 args
            "-feature",
            "-unchecked",
            "-Xfatal-warnings",
            "-Xlint",
            "-Yno-adapted-args",
            "-Ywarn-dead-code",
            "-Ywarn-numeric-widen",
            "-Xfuture"
        ),
        testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v"),
        ScalariformKeys.preferences := ScalariformKeys.preferences.value
                .setPreference(DoubleIndentClassDeclaration, true)
                .setPreference(PreserveSpaceBeforeArguments, true)
                .setPreference(CompactControlReadability, true)
                .setPreference(SpacesAroundMultiImports, false)
        )

lazy val root =
    project.in(file("."))
            .settings(commonSettings)
            .settings(Seq(
        publishArtifact := false,
        publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))))
            .aggregate(core)

lazy val core = project
        .enablePlugins(AutomateHeaderPlugin)
        .settings(commonSettings)
        .settings(Seq(
    name := "surrogate-service",
    libraryDependencies ++= commonDependencies ++ coreDependencies
))