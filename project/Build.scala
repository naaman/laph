import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "laph"
  val appVersion      = "0.1"

  val appDependencies = Seq(
    "nl.rhinofly" %% "api-s3" % "2.6.1"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers += "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"
  )

}
