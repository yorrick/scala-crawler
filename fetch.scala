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


def buildClient(): NingWSClient = {
	val config = new NingAsyncHttpClientConfigBuilder(DefaultWSClientConfig()).build
    val builder = new AsyncHttpClientConfig.Builder(config)
    new NingWSClient(builder.build)
}

def getBaseURL(nodes: NodeSeq): Try[String] = {
	val text = (nodes \ "Identify" \ "baseURL").text.trim
	
	if (text.isEmpty) {
		Failure(new Exception("Could not find baseURL"))
	} else {
		Success(text)
	}
}

def processResponse(response: WSResponse): Try[(String, String)] = {
  for {
		xml <- Try(XML.loadString(response.body) )
		url <- getBaseURL(xml)
		name <- Success((xml \ "Identify" \ "repositoryName").text.trim)
	} yield (url, name)
}


val client = buildClient()

val identifyCalls: Iterator[Future[WSResponse]] = stdin.getLines.filterNot(_.isEmpty) map { url =>
	println(s"Fetching $url")
	client.url(url).withQueryString("verb" -> "Identify").get
}

val completed: Future[Iterator[WSResponse]] = Future.sequence(identifyCalls)

completed map { responses =>
	val results = responses map processResponse
	results foreach println
	
	// close client once everything is done
	client.close()
}

