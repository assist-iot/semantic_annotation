ThisBuild / version := "0.8.0-SNAPSHOT"

//ThisBuild / scalaVersion := "3.1.1"O
ThisBuild / scalaVersion := "2.13.10"

Compile / run / mainClass := Some("org.sripas.seaman.DulceDeLeche")
Compile / packageBin / mainClass := Some("org.sripas.seaman.DulceDeLeche")

Test / fork := true

//val carmlVersion = "0.4.0-beta-5"
val carmlVersion = "0.3.2"
val rioFormatParserVersion = "3.4.0" // This is the version used by carml 0.3.2. Update it, when lifting CARML verison!

val akkaVersion = "2.6.19"
val akkaHttpVersion = "10.2.9"
val alpakkaVersion = "3.0.4"
val alpakkaKafkaVersion = "3.0.0"
val alpakkaMQTTVersion = "3.0.4"

val swaggerAkkaVersion = "2.7.0"
val swaggerVersion = "2.2.0"
val jakartaWSVersion = "3.1.0"
val webjarsLocatorVersion = "0.45"
val webjarsSwaggerUIVersion = "4.10.3"

val scalaXMLVersion = "2.1.0"

val logbackVersion = "1.2.11"
val janinoVersion = "3.1.9"
val JULToSLF4JVersion = "1.7.36"

val munitVersion = "0.7.29"
val scalatestVersion = "3.2.12"
val scalacheckVersion = "3.2.11.0"
val testcontainersScalaVersion = "0.40.8"

val mongoDbDriverVersion = "4.6.0"

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "SemAnnStreamer_core",
    idePackagePrefix := Some("org.sripas.seaman"),
    buildInfoKeys := Seq(name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "buildinfo",

    libraryDependencies ++= List(
      // Akka streams
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-kafka" % alpakkaKafkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-mqtt" % alpakkaMQTTVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-mqtt-streaming" % alpakkaMQTTVersion,
      // Akka http
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      // Swagger
//      "com.github.swagger-akka-http" %% "swagger-akka-http" % swaggerAkkaVersion,
//      "io.swagger.core.v3" % "swagger-jaxrs2-jakarta" % swaggerVersion,
//      "jakarta.ws.rs" % "jakarta.ws.rs-api" % jakartaWSVersion,
//      "org.webjars" % "webjars-locator" % webjarsLocatorVersion,
//      "org.webjars" % "swagger-ui" % webjarsSwaggerUIVersion,
      // MongoBD
      "org.mongodb.scala" %% "mongo-scala-driver" % mongoDbDriverVersion,
      // Logging
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.codehaus.janino" % "janino" % janinoVersion,
      "org.slf4j" % "jul-to-slf4j" % JULToSLF4JVersion
    ),

    libraryDependencies ++= List(
      // CARML
      // https://github.com/carml/carml
      // https://mvnrepository.com/artifact/com.taxonic.carml/carml
      //      "com.taxonic.carml" % "carml" % carmlV pomOnly(),
      "com.taxonic.carml" % "carml-engine" % carmlVersion,
      // CARML resolvers
      "com.taxonic.carml" % "carml-logical-source-resolver-jsonpath" % carmlVersion,
      "com.taxonic.carml" % "carml-logical-source-resolver-xpath" % carmlVersion,
      "com.taxonic.carml" % "carml-logical-source-resolver-csv" % carmlVersion,
      // Additional output formats
      "org.eclipse.rdf4j" % "rdf4j-rio-jsonld" % rioFormatParserVersion,
      // For XML verification
      "org.scala-lang.modules" %% "scala-xml" % scalaXMLVersion,

    ),

    libraryDependencies ++= List(
      // Testing
      //      "org.scalameta" %% "munit" % munitVersion % Test,
      "org.scalatest" %% "scalatest" % scalatestVersion % Test,
      "org.scalatestplus" %% "scalacheck-1-15" % scalacheckVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-scalatest" % testcontainersScalaVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-kafka" % testcontainersScalaVersion % Test,
//      "com.dimafeng" %% "testcontainers-scala-rabbitmq" % testcontainersScalaVersion % Test,
      "com.dimafeng" %% "testcontainers-scala-mongodb" % testcontainersScalaVersion % Test,
    ),


    testFrameworks ++= List(
//      TestFramework("munit.Framework"),
      TestFramework("scalatest.Framework")
    )
  )
