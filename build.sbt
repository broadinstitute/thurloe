name := "thurloe"
version := "0.1"
organization := "org.broadinstitute"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/"
)

val sprayV = "1.3.3"
val downgradedSprayV = "1.3.1"
val akkaV = "2.3.12"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % "2.11.7",
  "org.webjars" % "swagger-ui" % "2.1.1",
  "io.spray" %% "spray-can" % sprayV,
  "io.spray" %% "spray-routing" % sprayV,
  "io.spray" %% "spray-client" % sprayV,
  "io.spray" %% "spray-http" % sprayV,
  "io.spray" %% "spray-json" % downgradedSprayV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.slick" %% "slick" % "3.0.2",
  "com.typesafe" % "config" % "1.3.0",
  "org.slf4j" % "slf4j-nop" % "1.6.4",
  "commons-io" % "commons-io" % "2.4",
  "commons-codec" % "commons-codec" % "1.10",
  "mysql" % "mysql-connector-java" % "5.1.35",
  "org.liquibase" % "liquibase-core" % "3.3.5",
  "org.hsqldb" % "hsqldb" % "2.3.2",
  //---------- Test libraries -------------------//
  "io.spray" %% "spray-testkit" % sprayV % Test,
  "org.scalatest" %% "scalatest" % "2.2.5" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "org.liquibase" % "liquibase-core" % "3.3.5" % Test
)

releaseSettings

shellPrompt := { state => "%s| %s> ".format(GitCommand.prompt.apply(state), version.value)}

assemblyJarName in assembly := "thurloe-" + version.value + ".jar"

logLevel in assembly := Level.Info