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
import scala.concurrent.ExecutionContext.Implicits.global

// val config = new NingAsyncHttpClientConfigBuilder(DefaultWSClientConfig()).build()
// val builder = new AsyncHttpClientConfig.Builder(config)
// val wsClient:WSClient = new NingWSClient(builder.build())


def buildClient(): NingWSClient = {
	val clientConfig = new DefaultWSClientConfig()
	val secureDefaults: AsyncHttpClientConfig = new NingAsyncHttpClientConfigBuilder(clientConfig).build()
	// You can directly use the builder for specific options once you have secure TLS defaults...
	val builder = new AsyncHttpClientConfig.Builder(secureDefaults)
	builder.setCompressionEnabled(true)
	val secureDefaultsWithSpecificOptions: AsyncHttpClientConfig = builder.build()
	new NingWSClient(secureDefaultsWithSpecificOptions)
}


val client = buildClient()


println("Fetching google")
val response = client.url("http://google.com").get

response.foreach(r => {
	println(r.body) 
	// not the best place to close the client, 
	// but it ensures we dont close the threads before the response arrives 
	client.close()
})


