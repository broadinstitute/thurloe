name := "thurloe"

version := "0.1"

organization := "org.broadinstitute"

scalaVersion := "2.11.8"

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/"
)

val sprayV = "1.3.3"

val downgradedSprayV = "1.3.1"

val akkaV = "2.3.12"
val slickV = "3.1.0"

val lenthallV = "0.14-2ce072a-SNAPSHOT"
val workbenchV = "0.1-e09cf41-SNAP"

resolvers ++= Seq(
  "Broad Artifactory Releases" at "https://artifactory.broadinstitute.org/artifactory/libs-release/",
  "Broad Artifactory Snapshots" at "https://artifactory.broadinstitute.org/artifactory/libs-snapshot/")

libraryDependencies ++= Seq(
  "org.broadinstitute" %% "lenthall" % lenthallV,
  "org.broadinstitute.dsde" %%  "rawls-model"  % workbenchV,
  "org.broadinstitute.dsde" %%  "workbench-google"  % workbenchV,
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
  "commons-io" % "commons-io" % "2.4",
  "commons-codec" % "commons-codec" % "1.10",
  "mysql" % "mysql-connector-java" % "5.1.35",
  "org.liquibase" % "liquibase-core" % "3.3.5",
  "org.hsqldb" % "hsqldb" % "2.3.2",
  "com.sendgrid" % "sendgrid-java" % "2.2.2",
  "ch.qos.logback" % "logback-classic" % "1.1.6",
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
scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xmax-classfile-name", "200")

shellPrompt := { state => "%s| %s> ".format(GitCommand.prompt.apply(state), version.value)}

assemblyJarName in assembly := "thurloe-" + version.value + ".jar"

logLevel in assembly := Level.Info

// This appears to do some magic to configure itself. It consistently fails in some environments
// unless it is loaded after the settings definitions above.
Revolver.settings

mainClass in Revolver.reStart := Some("thurloe.Main")

Revolver.enableDebugging(port = 5050, suspend = false)

// When JAVA_OPTS are specified in the environment, they are usually meant for the application
// itself rather than sbt, but they are not passed by default to the application, which is a forked
// process. This passes them through to the "re-start" command, which is probably what a developer
// would normally expect.
javaOptions in Revolver.reStart ++= sys.env("JAVA_OPTS").split(" ").toSeq