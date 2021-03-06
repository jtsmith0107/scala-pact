package com.itv.scalapact.plugin.publish

import com.itv.scalapactcore.common.ColourOuput._
import com.itv.scalapactcore.common.{ConfigAndPacts, Helpers, PactBrokerAddressValidation}
import com.itv.scalapactcore.ScalaPactWriter

import scalaj.http.{Http, HttpResponse}
import scalaz.{-\/, \/-}

object Publisher {

  lazy val publishToBroker: String => String => ConfigAndPacts => Unit = pactBrokerAddress => versionToPublishAs => configAndPacts => {

    configAndPacts.pacts.foreach { pact =>
      val details = for {
        validatedAddress <- PactBrokerAddressValidation.checkPactBrokerAddress(pactBrokerAddress)
        providerName <- Helpers.urlEncode(pact.provider.name)
        consumerName <- Helpers.urlEncode(pact.consumer.name)
      } yield (validatedAddress, providerName, consumerName)

      details match {
        case -\/(l) =>
          println(l.red)

        case \/-((b, p, c)) =>
          //Not sure how I feel about this. Should you be able to publish snapshots? Pact broker will return these with a call to `/latest` ...
          val address = b + "/pacts/provider/" + p + "/consumer/" + c + "/version/" + versionToPublishAs.replace("-SNAPSHOT", ".x")

          println(s"Publishing to: $address".yellow)

          Http(address).header("Content-Type", "application/json").postData(ScalaPactWriter.pactToJsonString(pact)).method("PUT").asString match {
            case r: HttpResponse[String] if r.is2xx => println("Success".green)
            case r: HttpResponse[String] =>
              println(r)
              println(s"Failed: ${r.code}, ${r.body}".red)
          }
      }
    }

    Unit
  }
}
