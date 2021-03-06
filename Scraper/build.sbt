name := "AnimeSiteScrapper"

version := "0.1"

scalaVersion := "2.13.3"

enablePlugins(GraalVMNativeImagePlugin)
containerBuildImage := Some("AnimeSiteScrapper/graalvm-ce-native-image:21.0.0.2")
graalVMNativeImageOptions ++= Seq(
	"--allow-incomplete-classpath",
    "--trace-class-initialization=org.slf4j.impl.SimpleLogger",
)


libraryDependencies += "net.ruippeixotog" %% "scala-scraper" % "2.2.0"
libraryDependencies += "com.softwaremill.sttp.client" %% "core" % "2.2.5"
libraryDependencies += "com.google.code.gson" % "gson" % "2.8.6"
libraryDependencies += "io.ino" %% "solrs" % "2.4.2"
libraryDependencies += "commons-codec" % "commons-codec" % "1.14"
libraryDependencies ++= Seq("org.slf4j" % "slf4j-api" % "1.7.5",
    "org.slf4j" % "slf4j-simple" % "1.7.5")
libraryDependencies += "org.bouncycastle" % "bcprov-jdk16" % "1.46"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % Test


lazy val core: Project = (project in file("core"))
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaServerAppPackaging)
  .settings(
    packageName in Docker := "docker-test"
  )

