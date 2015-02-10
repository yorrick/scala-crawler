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


def buildClient(): NingWSClient = {
	val config = new NingAsyncHttpClientConfigBuilder(DefaultWSClientConfig()).build
    val builder = new AsyncHttpClientConfig.Builder(config)
    new NingWSClient(builder.build)
}

def processResponse(response: WSResponse): String = {
	response.body
}


val client = buildClient()

val identifyCalls: Seq[Future[WSResponse]] = args map { url =>
	client.url(url).withQueryString("verb" -> "Identify").get
}

val completed: Future[Seq[WSResponse]] = Future.sequence(identifyCalls)

completed map { responses =>
	client.close()

	val results = responses map processResponse

	results foreach println
}

// client.close()



