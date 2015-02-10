#!/usr/bin/env scalas


/***
scalaVersion := "2.10.4"

resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.3.7"
)
*/

import com.ning.http.client._
import play.api.libs.ws.ning._
import play.api.libs.ws._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source.stdin
import scala.util.{Success, Failure, Try}
import scala.xml.{NodeSeq, XML}
import scala.concurrent.duration._


def buildClient(): NingWSClient = {
	val config = new NingAsyncHttpClientConfigBuilder(DefaultWSClientConfig()).build
    val builder = new AsyncHttpClientConfig.Builder(config)
    new NingWSClient(builder.build)
}


def processResponse(response: WSResponse): (String, String) = Try {
	val xml = XML.loadString(response.body)
	val url = (xml \ "Identify" \ "baseURL").text.trim
	val name = (xml \ "Identify" \ "repositoryName").text.trim
	
	(url, name)
} getOrElse(("None", "None"))


val client = buildClient()

val identifyCalls: Iterator[(String, Future[(String, String)])] = stdin.getLines.filterNot(_.isEmpty) map { url =>
	println(s"Fetching $url")
	
	
	val response: Future[WSResponse] = client.url(url).withQueryString("verb" -> "Identify").get
	val result: Future[(String, String)] = response.map(processResponse).andThen {
		case Success(tuple) => println(s"$url: $tuple")
		case Failure(t) => println(s"$url: Error found")
	}
		
	(url, result)
}


// wait for the futures to complete before closing the client
val completed: Future[_] = Future.sequence(identifyCalls.map(tuple => tuple._2))
println("Waiting for processing to finish")
Await.result(completed, 10 minutes)
println("Closing client")
client.close()

