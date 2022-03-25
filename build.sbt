import sbtassembly.MergeStrategy

name := "thurloe"

version := "0.1"

organization := "org.broadinstitute"

scalaVersion := "2.13.8"

val akkaV = "2.6.18"
val akkaHttpV = "10.2.7"
val slickV = "3.3.3"
val workbenchGoogleV = "0.21-31be16e8-SNAP"
val rawlsModelV = "0.1-384ab501b"
val scalaTestV = "3.2.11"

resolvers ++= Seq(
  "Broad Artifactory Releases" at "https://broadinstitute.jfrog.io/broadinstitute/libs-release/",
  "Broad Artifactory Snapshots" at "https://broadinstitute.jfrog.io/broadinstitute/libs-snapshot/")

libraryDependencies ++= Seq(
  "org.webjars" % "swagger-ui" % "4.1.3",
  "org.broadinstitute.dsde" %%  "rawls-model" % rawlsModelV
    exclude("bio.terra", "workspace-manager-client"),

  "org.broadinstitute.dsde.workbench" %%  "workbench-google" % workbenchGoogleV
    exclude("com.typesafe.akka", "akka-protobuf-v3_2.13")
    exclude("com.google.protobuf", "protobuf-java")
    exclude("org.bouncycastle", "bcprov-jdk15on")
    exclude("org.bouncycastle", "bcprov-ext-jdk15on")
    exclude("org.bouncycastle", "bcutil-jdk15on")
    exclude("org.bouncycastle", "bcpkix-jdk15on"),
  "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.akka" %% "akka-stream" % akkaV
    exclude("com.typesafe.akka", "akka-protobuf-v3_2.13")
    exclude("com.google.protobuf", "protobuf-java"),
  "com.typesafe.slick" %% "slick" % slickV,
  "com.typesafe.slick" %% "slick-hikaricp" % slickV,
  "com.typesafe" % "config" % "1.4.2",
  "commons-io" % "commons-io" % "2.11.0",
  "commons-codec" % "commons-codec" % "1.15",
  "mysql" % "mysql-connector-java" % "8.0.28",
  "org.liquibase" % "liquibase-core" % "4.7.1",
  "org.hsqldb" % "hsqldb" % "2.6.1",
  "com.sendgrid" % "sendgrid-java" % "2.2.2",
  "ch.qos.logback" % "logback-classic" % "1.2.10",
  //---------- Test libraries -------------------//
  "org.broadinstitute.dsde.workbench" %%  "workbench-google" % workbenchGoogleV % Test classifier "tests",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % Test,
  "org.scalatest" %% "scalatest" % scalaTestV % Test,
  "org.yaml" % "snakeyaml" % "1.30" % Test
)

scalacOptions ++= Seq(
  "-target:jvm-11",
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
  "-language:higherKinds",             // Allow higher-kinded types
  "-language:implicitConversions",     // Allow definition of implicit functions called views
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
  "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
  "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
  "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",            // Option.apply used implicit view.
  "-Xlint:package-object-classes",     // Class or object defined in package object.
  "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods arClusterComponent.scalae not visible as view bounds.
  "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
//  "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
//  "-Ywarn-value-discard",               // Warn when non-Unit expression results are unused.
  "-language:postfixOps"
)

assemblyJarName := "thurloe-" + version.value + ".jar"

val customMergeStrategy: String => MergeStrategy = {
  case PathList("META-INF", xs @ _*) =>
    (xs map {_.toLowerCase}) match {
      case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) => MergeStrategy.discard
      case _ => MergeStrategy.last
    }
  case "NOTICE" => MergeStrategy.discard
  case "module-info.class" => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.deduplicate
}

assemblyMergeStrategy := customMergeStrategy
