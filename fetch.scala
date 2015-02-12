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
import scala.xml.{Node, NodeSeq, XML}
import scala.concurrent.duration._


def buildClient(): NingWSClient = {
	val config = new NingAsyncHttpClientConfigBuilder(DefaultWSClientConfig()).build
    val builder = new AsyncHttpClientConfig.Builder(config)
    new NingWSClient(builder.build)
}



val CSV_SEPARATOR = "#"




case class Identify(queriedUrl: String, name: String = "", url: String = "") {
	lazy val toCSV: String = s"$queriedUrl$CSV_SEPARATOR$name$CSV_SEPARATOR$url"
}

object Identify {
	def fromWSResponse(queriedUrl: String, response: WSResponse) = Try {
		val xml = XML.loadString(response.body)
		val url = (xml \ "Identify" \ "baseURL").text.trim
		val name = (xml \ "Identify" \ "repositoryName").text.trim

		Identify(queriedUrl, url, name)
	} getOrElse(Identify(queriedUrl))
}





//case class ListMetadataFormats() {
//	def toCSV: String = s""
//}
//
//object ListMetadataFormats {
//	def fromWSResponse(response: WSResponse) = Try {
//		val xml = XML.loadString(response.body)
//
//		ListMetadataFormats()
//	} getOrElse(ListMetadataFormats())
//}




case class ListIdentifiers(articleNumber: Option[Int] = None) {
	lazy val toCSV = articleNumber.map(_.toString).getOrElse("")
}

object ListIdentifiers {
	def fromWSResponse(response: WSResponse) = Try {
		val xml = XML.loadString(response.body)
		
		val completeListSizeAttributes: NodeSeq = (xml \ "ListIdentifiers" \ "resumptionToken") flatMap { _.attribute("completeListSize").getOrElse(Seq()) }
		val articleNumber = Try(completeListSizeAttributes.text.toInt).toOption
		
		ListIdentifiers(articleNumber)
	} getOrElse(ListIdentifiers())
	
}




System.err.println("Opening connection")
val client = buildClient()

// http://doaj.org/oai.article?verb=Identify
def identify(url: String) = client.url(url).withQueryString("verb" -> "Identify")
// http://doaj.org/oai.article?metadataPrefix=oai_dc&verb=ListIdentifiers
def listIdentifiers(url: String) = client.url(url).withQueryString("verb" -> "ListIdentifiers", "metadataPrefix" -> "oai_dc")


val identifyCalls: Iterator[Future[(Identify, ListIdentifiers)]] = stdin.getLines.filterNot(_.isEmpty) map { url =>
	System.err.println(s"Fetching $url")

	val queryResults = for {
		identify <- identify(url).get.map(r => Identify.fromWSResponse(url, r))
		listIdentifiers <- listIdentifiers(url).get.map(r => ListIdentifiers.fromWSResponse(r))
	} yield (identify, listIdentifiers)
		
	val futureRefs: Future[(Identify, ListIdentifiers)] = queryResults andThen {
		case Success((identify, listIdentifiers)) => {
			println(s"${identify.toCSV}$CSV_SEPARATOR${listIdentifiers.toCSV}")
		}
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

