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
val slickV = "3.1.0"

val lenthallV = "0.14-2ce072a-SNAPSHOT"

resolvers ++= Seq(
  "Broad Artifactory Releases" at "https://artifactory.broadinstitute.org/artifactory/libs-release/",
  "Broad Artifactory Snapshots" at "https://artifactory.broadinstitute.org/artifactory/libs-snapshot/")

libraryDependencies ++= Seq(
  "org.broadinstitute" %% "lenthall" % lenthallV,
  "org.scala-lang" % "scala-reflect" % "2.11.7",
  "org.webjars" % "swagger-ui" % "2.1.1",
  "io.spray" %% "spray-can" % sprayV,
  "io.spray" %% "spray-routing" % sprayV,
  "io.spray" %% "spray-client" % sprayV,
  "io.spray" %% "spray-http" % sprayV,
  "io.spray" %% "spray-json" % downgradedSprayV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.slick" %% "slick" % slickV,
  "com.typesafe.slick" %% "slick-hikaricp" % slickV,
  "com.typesafe" % "config" % "1.3.0",
  "org.slf4j" % "slf4j-nop" % "1.6.4",
  "commons-io" % "commons-io" % "2.4",
  "commons-codec" % "commons-codec" % "1.10",
  "mysql" % "mysql-connector-java" % "5.1.35",
  "org.liquibase" % "liquibase-core" % "3.3.5",
  "org.hsqldb" % "hsqldb" % "2.3.2",
  "com.sendgrid" % "sendgrid-java" % "2.2.2",
  //---------- Test libraries -------------------//
  "io.spray" %% "spray-testkit" % sprayV % Test,
  "org.scalatest" %% "scalatest" % "2.2.5" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "org.liquibase" % "liquibase-core" % "3.3.5" % Test,
  "org.yaml" % "snakeyaml" % "1.16" % Test
)

releaseSettings

// The reason why -Xmax-classfile-name is set is because this will fail
// to build on Docker otherwise.  The reason why it's 200 is because it
// fails if the value is too close to 256 (even 254 fails).  For more info:
//
// https://github.com/sbt/sbt-assembly/issues/69
// https://github.com/scala/pickling/issues/10
scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xmax-classfile-name", "200")

shellPrompt := { state => "%s| %s> ".format(GitCommand.prompt.apply(state), version.value)}

assemblyJarName in assembly := "thurloe-" + version.value + ".jar"

logLevel in assembly := Level.Info
