val tapirVersion = "1.9.5"
val http4sVersion = "0.23.24"
//val http4sVersion = "1.0.0-M40"
enablePlugins(DockerPlugin, DockerComposePlugin, JavaAppPackaging)

lazy val rootProject = (project in file(".")).settings(
  Seq(
    name := "scalac-githubrank",
    version := "0.1.0-SNAPSHOT",
    organization := "io.scalac.test",
    scalaVersion := "3.3.0",
    libraryDependencies ++= Seq(
      "com.47deg" %% "github4s" % "0.32.1",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-client" % tapirVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",
//      "io.circe" %% "circe-generic" % "0.14.5",
//      "io.circe" %% "circe-literal" % "0.14.5",
      "com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-pickler" % tapirVersion,
      "ch.qos.logback" % "logback-classic" % "1.4.8",
      "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.16" % Test,
      "com.softwaremill.sttp.client3" %% "circe" % "3.8.15" % Test
    )
  )
)

Compile / run / fork := true
run / connectInput := true

scalacOptions ++= Seq("-Xmax-inlines", "500")

//Docker / mappings := mappings.value
//Compile / mainClass := Some("io.scalac.Main")
Docker / daemonUserUid := Some("1009") // 1001 was used in base image
dockerExposedPorts := Seq(8080)
dockerBaseImage := "sbtscala/scala-sbt:graalvm-community-21.0.1_1.9.7_3.3.1"

// docker compose
dockerImageCreationTask := (Docker / publishLocal).value
composeRemoveContainersOnShutdown := true
//composeFile := baseDirectory.value +  "/docker/docker-compose.yml"// Specify the full path to the Compose File to use to create your test instance. It defaults to docker-compose.yml in your resources folder.
