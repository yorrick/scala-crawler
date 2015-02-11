#!/usr/bin/env scalas


/***
scalaVersion := "2.10.4"

resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"
	
logLevel := Level.Error

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


case class Identify(queriedUrl: String, name: String = "", url: String = "") {
	def toCSV: String = s"$queriedUrl,$name,$url"
}

object Identify {
	def fromWSResponse(queriedUrl: String, response: WSResponse) = Try {
		val xml = XML.loadString(response.body)
		val url = (xml \ "Identify" \ "baseURL").text.trim
		val name = (xml \ "Identify" \ "repositoryName").text.trim

		Identify(queriedUrl, url, name)
	} getOrElse(Identify(queriedUrl))
}


case class ListMetadataFormats() {
	def toCSV: String = s""
}

object ListMetadataFormats {
	def fromWSResponse(response: WSResponse) = Try {
		val xml = XML.loadString(response.body)

		ListMetadataFormats()
	} getOrElse(ListMetadataFormats())
}



System.err.println("Opening connection")
val client = buildClient()

val identifyCalls: Iterator[Future[(Identify, ListMetadataFormats)]] = stdin.getLines.filterNot(_.isEmpty) map { url =>
	System.err.println(s"Fetching $url")

	val queryResults = for {
		identify <- client.url(url).withQueryString("verb" -> "Identify").get.map(r => Identify.fromWSResponse(url, r))
		listMetadataFormats <- client.url(url).withQueryString("verb" -> "ListMetadataFormats").get.map(r => ListMetadataFormats.fromWSResponse(r))
	} yield (identify, listMetadataFormats)
		
	val futureRefs: Future[(Identify, ListMetadataFormats)] = queryResults andThen {
		case Success((identify, listMetadataFormats)) => println(s"${identify.toCSV},${listMetadataFormats.toCSV}")
		case Failure(t) => System.err.println(s"$url: Error found")
	}

	futureRefs
}


// wait for the futures to complete before closing the client
val completed: Future[_] = Future.sequence(identifyCalls)
System.err.println("Waiting for processing to finish")
Await.result(completed, 10 minutes)
System.err.println("Closing client")
client.close()

