addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.4.1")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")

addSbtPlugin(
  "com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1"
) // Use `unusedCompileDependencies` to see unused dependencies

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")

addDependencyTreePlugin
