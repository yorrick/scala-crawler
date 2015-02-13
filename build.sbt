name := "scala-scripts"

version := "1.0.0"

scalaVersion := "2.10.4"

resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

logLevel := Level.Error

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.3.7" withSources() withJavadoc()
)

