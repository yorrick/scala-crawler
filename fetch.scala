#!/usr/bin/env scalas


/***
scalaVersion := "2.10.4"

resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

logLevel := Level.Error

traceLevel := -1

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



val CSV_SEPARATOR = ";"
val REQUEST_TIMEOUT = 6000


def cleanString(string: String) = string.replaceAll(CSV_SEPARATOR, "").replaceAll("\n", "")


case class Identify(name: String = "", url: String = "") {
	lazy val toCSV: String = s"$CSV_SEPARATOR$name$CSV_SEPARATOR$url"
}

object Identify {
	
	def fromWSResponse(response: WSResponse): Future[Identify] = Future {
		val xml = XML.loadString(response.body)
		val url = cleanString((xml \ "Identify" \ "baseURL").text.trim)
		val name = cleanString((xml \ "Identify" \ "repositoryName").text.trim)

		Identify(url, s"'$name'")
	} recover { 
		case _ => Identify()
	}
	
}



case class ListIdentifiers(articleNumber: Option[Int] = None) {
	lazy val toCSV = articleNumber.map(_.toString).getOrElse("")
}

object ListIdentifiers {
	
	def fromWSResponse(response: WSResponse) = Future {
		val xml = XML.loadString(response.body)

		val completeListSizeAttributes: NodeSeq = (xml \ "ListIdentifiers" \ "resumptionToken") flatMap { node =>
			node.attribute("completeListSize").getOrElse(Seq())
		}
		val articleNumber = Try(completeListSizeAttributes.text.toInt).toOption

		ListIdentifiers(articleNumber)
	} recover { 
		case _ => ListIdentifiers() 
	}
	
}




case class Info(queriedUrl: String, identify: Identify, listIdentifiers: ListIdentifiers) {
	lazy val toCSV = s"$queriedUrl${identify.toCSV}$CSV_SEPARATOR${listIdentifiers.toCSV}"
}



System.err.println("Opening connection")
val client = buildClient()


// http://doaj.org/oai.article?verb=Identify
def identify(url: String): Future[WSResponse] = try {
	client.url(url)
		.withRequestTimeout(REQUEST_TIMEOUT)
		.withFollowRedirects(true)
		.withQueryString(
				"verb" -> "Identify"
		).get
	} catch {
		case t: Throwable => Future.failed(t)
	}

// http://doaj.org/oai.article?metadataPrefix=oai_dc&verb=ListIdentifiers
def listIdentifiers(url: String): Future[WSResponse] = 	client.url(url)
	.withRequestTimeout(REQUEST_TIMEOUT)
	.withFollowRedirects(true)
	.withQueryString(
		"verb" -> "ListIdentifiers",
		"metadataPrefix" -> "oai_dc"
).get



val identifyCalls: Iterator[Future[Info]] = stdin.getLines.filterNot(_.isEmpty) map { url =>
	System.err.println(s"Fetching $url")

	// TODO check that if list identifiers fails, identify info is still returned
	val queryResults: Future[Info] = for {
		identifyResponse <- identify(url)
		identify <- Identify.fromWSResponse(identifyResponse)
		listIdentifiersResponse <- listIdentifiers(url)
		listIdentifiers <- ListIdentifiers.fromWSResponse(listIdentifiersResponse)
	} yield Info(url, identify, listIdentifiers)

	val futureRefs: Future[Info] = queryResults recover {
		case t: Throwable => Info(url, Identify(), ListIdentifiers())
	} andThen {
		case Success(info) => println(info.toCSV)
		case Failure(t) => System.err.println(s"$url: Error found")
	}

	futureRefs
}


// wait for the futures to complete before closing the client
val completed: Future[_] = Future.sequence(identifyCalls)

try {
	System.err.println("Waiting for processing to finish")
	Await.result(completed, 1 minutes)
} catch {
	case t: Throwable => System.err.println(s"Got error waiting for process to finish $t")
} finally {
	System.err.println("Closing client")
	client.close()
}

