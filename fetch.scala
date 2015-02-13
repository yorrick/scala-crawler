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

	// http://doaj.org/oai.article?verb=Identify
	def identify(client: WSClient, url: String): Future[WSResponse] = try {
		client.url(url)
			.withRequestTimeout(REQUEST_TIMEOUT)
			.withFollowRedirects(true)
			.withQueryString(
				"verb" -> "Identify"
			).get
	} catch {
		case t: Throwable => Future.failed(t)
	}
	
	def fromWSResponse(response: WSResponse): Future[Identify] = Future {
		if (response.status == 200) {
			val xml = XML.loadString(response.body)
			val url = cleanString((xml \ "Identify" \ "baseURL").text.trim)
			val name = cleanString((xml \ "Identify" \ "repositoryName").text.trim)
	
			Identify(url, s"'$name'")
		} else {
			Identify()
		}
	} recover { 
		case _ => Identify()
	}
	
}



case class ListIdentifiers(articleNumber: Option[Int] = None) {
	lazy val toCSV = articleNumber.map(_.toString).getOrElse("")
}

object ListIdentifiers {

	// http://doaj.org/oai.article?metadataPrefix=oai_dc&verb=ListIdentifiers
	def listIdentifiers(client: WSClient, url: String): Future[WSResponse] = client.url(url)
		.withRequestTimeout(REQUEST_TIMEOUT)
		.withFollowRedirects(true)
		.withQueryString(
			"verb" -> "ListIdentifiers",
			"metadataPrefix" -> "oai_dc"
		).get

	def fromWSResponse(response: WSResponse) = Future {
		if (response.status == 200) {
			val xml = XML.loadString(response.body)
	
			val completeListSizeAttributes: NodeSeq = (xml \ "ListIdentifiers" \ "resumptionToken") flatMap { node =>
				node.attribute("completeListSize").getOrElse(Seq())
			}
			val articleNumber = Try(completeListSizeAttributes.text.toInt).toOption
	
			ListIdentifiers(articleNumber)
		} else {
			ListIdentifiers()
		}
		
	} recover { 
		case _ => ListIdentifiers() 
	}
	
}


case class ListMetdataFormats(formatPrefixes: Seq[String] = Seq()) {
	lazy val toCSV = formatPrefixes.mkString("#")
}


object ListMetdataFormats {

	// http://doaj.org/oai.article?metadataPrefix=oai_dc&verb=ListIdentifiers
	def listMetadataFormats(client: WSClient, url: String): Future[WSResponse] = client.url(url)
		.withRequestTimeout(REQUEST_TIMEOUT)
		.withFollowRedirects(true)
		.withQueryString(
			"verb" -> "ListMetadataFormats"
		).get

	def fromWSResponse(response: WSResponse) = Future {
		
		if (response.status == 200) {
			val xml = XML.loadString(response.body)
			val formats = (xml \ "ListMetadataFormats" \ "metadataFormat" \ "metadataPrefix") map(_.text)
			ListMetdataFormats(formats.sorted)
		} else {
			ListMetdataFormats()
		}
		
	} recover { 
		case _ => ListMetdataFormats()
	}
	
}




case class Info(queriedUrl: String, identify: Identify, listIdentifiers: ListIdentifiers, listMetadataFormats: ListMetdataFormats) {
	lazy val toCSV = s"$queriedUrl${identify.toCSV}$CSV_SEPARATOR${listIdentifiers.toCSV}$CSV_SEPARATOR${listMetadataFormats.toCSV}"
}



System.err.println("Opening connection")
val client = buildClient()




val identifyCalls: Iterator[Future[Info]] = stdin.getLines.filterNot(_.isEmpty) map { url =>
	System.err.println(s"Fetching $url")

	// TODO check that if list identifiers fails, identify info is still returned
	val queryResults: Future[Info] = for {
		
		identifyResponse <- Identify.identify(client, url)
		identify <- Identify.fromWSResponse(identifyResponse)
	
		listIdentifiersResponse <- ListIdentifiers.listIdentifiers(client, url)
		listIdentifiers <- ListIdentifiers.fromWSResponse(listIdentifiersResponse)
	
		listMetadataFormatsResponse <- ListMetdataFormats.listMetadataFormats(client, url)
		listMetadataFormats <- ListMetdataFormats.fromWSResponse(listMetadataFormatsResponse)
		
	} yield Info(url, identify, listIdentifiers, listMetadataFormats)

	val futureRefs: Future[Info] = queryResults recover {
		case t: Throwable => Info(url, Identify(), ListIdentifiers(), ListMetdataFormats())
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
	Await.result(completed, 10 minutes)
} catch {
	case t: Throwable => System.err.println(s"Got error waiting for process to finish $t")
} finally {
	System.err.println("Closing client!")
	client.close()
}

