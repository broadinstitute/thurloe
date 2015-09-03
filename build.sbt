name := "thurloe"
version := "0.1"
organization := "org.broadinstitute"

scalaVersion := "2.11.7"

val sprayV = "1.3.2"
val DowngradedSprayV = "1.3.1"
val akkaV = "2.3.12"

libraryDependencies ++= Seq(
  "com.gettyimages" %% "spray-swagger" % "0.5.1",
  "org.webjars" % "swagger-ui" % "2.1.1",
  "io.spray" %% "spray-can" % sprayV,
  "io.spray" %% "spray-routing" % sprayV,
  "io.spray" %% "spray-client" % sprayV,
  "io.spray" %% "spray-http" % sprayV,
  "io.spray" %% "spray-json" % DowngradedSprayV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "commons-io" % "commons-io" % "2.4",
  "mysql" % "mysql-connector-java" % "5.1.35",
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