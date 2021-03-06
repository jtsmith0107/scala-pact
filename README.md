# ScalaPact
A library for generating Consumer Driven Contract files in Scala projects following the PACT standard using [ScalaTest](http://www.scalatest.org/). Includes supporting tools that use Pact files to verify and stub services.

ScalaPact is intended for Scala developers who are looking for a better way to manage the HTTP contracts between their services.

## Acknowledgments
ScalaPact is not an original idea - this project would never have happened without those who came before us:

1. [DiUS](https://github.com/DiUS)
1. [Pact Foundation](https://github.com/pact-foundation)
1. [Thoughtworks / Ian Robinson / Martin Fowler](http://martinfowler.com/articles/consumerDrivenContracts.html)

Of particular note is [Beth Skurrie](https://github.com/bethesque) - a lot of the design choices and ideas in ScalaPact are direct copies of the ones in her projects. If you're looking for a Ruby implementation, look no further!

## Setup Guide

### ScalaTest Library
Add the dependency to your `build.sbt` file like this:

```
libraryDependencies ++= Seq(
  "com.itv" %% "scalapact-scalatest" % "1.0.1" % "test"
)
```

### SBT Plugin
Add the plugin to your `project/plugins.sbt` file like this:

```
addSbtPlugin("com.itv.plugins" % "scalapact-plugin" % "1.0.1")
```

## Basic Usage Examples
### ScalaTest Pact Forger API
There is an example test spec that can be found [here](https://github.com/ITV/scalapact/blob/master/scalapact-scalatest/src/test/scala/com/itv/scalapact/ExampleSpec.scala). The hope is that this will be a living example spec.

### SBT Plugin Commands

### pact-test
You can run the Pact test cases just by executing `sbt test` as normal. Because of the way the library has been written, running the tests will generate a series of Pact JSON files, one for each interaction.

The generated Pact files will be output to your target directory under `<project root>/target/pacts`.

Usually it is desirable to condense the Pact files into one file per consumer / provider pair that contains all of the possible interactions.

Entering `sbt pact-test` will:

1. Run clean to remove any lingering pact files
1. Run the tests as normal to generate the Pact files
1. Squash the Pact files into one per consumer / provider pair

### pact-stubber
In order to test your service in isolation, you'll need to be able to stub out your service's upstream dependancies. Luckily, you've already defined all the behaviours you expect your provider to exhibit in your CDC Pact tests!

Running `sbt pact-stubber` will re-run your Pact tests and then use the generated Pact files to create a running stub service. The stub will accept requests and deliver the responses you defined in your test cases giving you predictable, maintainable behaviour.

You can also run the stubber using a combination of the following command line options, below are the defaults:

`sbt "pact-stubber --host localhost --port 1234 --source target/pacts"`

*Note that files in the source folder are recursively loaded.*

#### HTTP Administration
If you prefer, you can use the stubber dynamically by adding and removing pacts using HTTP. All calls must be made with a special header:

`X-Pact-Admin=true`

- `GET /interactions` returns a list of all currently loaded interactions
- `POST | PUT /interactions` accepts a Pact JSON string and adds all the interactions to the pool it matches against
- `DELETE /interactions` Clears all the current interactions so you can start again

### pact-publish
If you plan to use pact testing as part of your CI pipeline you'll probably want to be able to share pact files efficiently between builds. For example a consumer project's build generates a new version of the projects Pact files and they are then used during the providers CI build.

To achieve this we use the Ruby tool called Pact Broker (see below) and the publish command to update the files to it.

#### Command Line Options
Before we can publish, we have to tell Scala-Pact where it can find a running instance of Pact Broker by adding the following line to either `build.sbt` or `pact.sbt`:

`pactBrokerAddress := "http://my-pact-broker:4321"`

You can also specify the version you wish to publish under by adding:

`pactContractVersion := "1.0.0"`

If you omit this variable or set it to an empty string, the main project version will be the version used to publish against.

You can then use the publish command to generate and upload your pact files to pact broker:

`sbt pact-publish`

Note that your Pact files will have the same version number as the normal project version defined in your `build.sbt` file, because you versioned that breaking API change - right?

By default, Scala-Pact does not allow you to publish pact files from SNAPSHOT versions of your project (but takes into account the pactContractVersion if set). This is because it can confuse pact brokers understanding of the latest contract. If you wish to enable this behaviour, add the following line to your `pact.sbt` file:

`allowSnapshotPublish := true`

### pact-verify
Once the consumer has defined the contract as CDC tests and exported them to Pact files, they'll deliver them to their provider. The provider then exercises their own API using the Pact files via a verifier.

The verifier is quite a simple idea: load a Pact file, make all the requests and compare all the responses to the expected ones.

The ScalaPact verifier can be run by entering `sbt pact-verify`.

The verifier will write out JUnit results to the `target/test-reports` directory in order to fail builds.

#### Command Line Options
You can also run the verifier using a combination of the following command line options. Below are the defaults:

`sbt "pact-verify --host localhost --protocol http --port 1234 --source pacts"`

*Note that files in the source folder are recursively loaded. Specifying a local source folder takes precedence over loading remote files from Pact Broker (see below)*

#### Verifying with Pact Broker during a CI build
If you're using the publish command to send files to Pact Broker, you'll also want to know how to verify against them in the provider project.

To do this, you need to add the following to either your `build.sbt` or `pact.sbt` file:

```
pactBrokerAddress := "http://my-pact-broker:4321"
providerName := "The Name Of This Service"
consumerNames := Seq("Consumer A", "Consumer B")
```

Note: The names are **keys** and all have to line up. Downstream services must publish with the same names that you use to retrieve against.

You then run verify as normal **without** specifying a local folder i.e.:

`sbt "pact-verify --host localhost --port 1234"`

This causes the verifier to try and load it's Pacts from Pact Broker ahead of the normal verification process.

### Other Considerations

1. Mock servers can only understand an endpoint being in one state. Mostly that isn't a problem - if you want to create a Pact describing the look up of documents that result in a 200 or a 404 you simply look up two different documents. Where you have something like a `/status` endpoint that could come back in different states that you care about, you would have to be a bit creative, or not describe that behaviour in a pact contract.
1. ScalaTest runs in parallel by default so even clearing the state of the stubber between tests could, and probably would, result in errors if you were using HTTP administration.

## Provider States
ScalaPact currently offers limited support for provider states.

Sometimes, you need to warn your provider that a contract relies on the providing system being in a particular state. For example, your contract describes requesting a document resource via a GET request with the document's id as a query parameter. If you send the Pact contract to your provider, but that document id doesn't exist on their system, then verification will fail through no-ones fault.

To warn the provider about such requirements, we use provider states. In the tests these are simply strings in the `given()` method as below:

```
forgePact
  .between("My Consumer")
  .and("Their Provider Service")
  .addInteraction(
    interaction
      .description("Fetching a specific ID")
      .given("Resource with ID 1234 exists")
      .uponReceiving("/document/lookup?id=1234")
      .willRespondWith(200, "ID: 1234 Exists")
  )
  .runConsumerTest { mockConfig =>

    val result = SimpleClient.doGetRequest(mockConfig.baseUrl, endPoint, Map.empty)

    result.status should equal(200)
    result.body should equal("ID: 1234 Exists")

  }
```
[Example taken from the ExampleSpec test suite.](https://github.com/ITV/scalapact/blob/master/scalapact-scalatest/src/test/scala/com/itv/scalapact/ExampleSpec.scala)

The `given("Resource with ID 1234 exists")` string is actually a key that the provider can hook into! It shows up in the Pact contract under the `providerState` field like this:

```
{
  "provider" : {
    "name" : "Their Provider Service"
  },
  "consumer" : {
    "name" : "My Consumer"
  },
  "interactions" : [
    {
      "providerState" : "Resource with ID 1234 exists",
      "description" : "Fetching a specific ID",
      "request" : {
        "method" : "GET",
        "path" : "/provider-state",
        "query" : "id=1234"
      },
      "response" : {
        "status" : 200,
        "body" : "ID: 1234 Exists"
      }
    }
  ]
}
```

On the provider's side, before verification they can opt to take action on any of these keys by adding a `pact.sbt` file to the root of their Scala project. Here is an example of the contents, again taken from the example suite:

```
import com.itv.scalapact.plugin.ScalaPactPlugin._

providerStates := Seq(
  ("Resource with ID 1234 exists", (key: String) => {
    println("Injecting key 1234 into the database...")
    // Do some work to ensure the system under test is
    // in an appropriate state before verification
    true
  })
)
```

*Notice it's the same string!*

Now when the provider runs the verification, the function they wrote will be invoked prior to the relevant interaction being verified.

The providerStates settings object is a `Seq[(String, String => Boolean)]` where the first string is the `key` and the `String => Boolean` is a function you describe that simply takes the `key` and returns whether your code ran successfully or not.

The intention is to create helper objects in the future for running common tasks like executing shell scripts but at the moment the functions are pure Scala and it's up to you how you use them.

## Pact File Distribution via Pact Broker
During a build process you will need some mechanism for delivering the Pact files your consumer tests generate to your provider ready for verification.

You can do this any way you like, they are just JSON files after all, but we are currently exploring [pact-broker](https://github.com/bethesque/pact_broker). Pact Broker is a Ruby service that allows you to post and look up versioned Pact files.

*An opinionated note on versioning:* Focus on versioning your API's not your Pact files! It can be handy to have versions of Pact files around but you should not be pushing a breaking API changes and communicating them to your provider with versioned Pact files. A breaking API change is a new API version. Theoretically your provider should always be able to ask for the latest Pact files for each API version and have confidence that they are correct.

## Pact Tests vs Integration Tests
Technically, when you write a Pact test you are creating an integration test, in that:

1. You write some client code to make the call to your provider;
2. You then write a test using a mock that expects a request and gives a response to a real HTTP call;
3. You check the results are what you expected.

The *purpose* of Pact and Integration tests is different though. A Pact test is there to describe the agreed contract between one service and another from the perspective of the consumer. An integration test can describe the relationship but not in a way that you can share with your provider for verification. Additionally Integration tests are good for testing failure cases where Pact tests are not.

Consider these two statements:
1. Pact tests define *what* the agreement between a consumer and a provider is
2. Integration tests check *how* that agreement is implemented on the consumer side

For instance, you should use Pact tests for describing the agreement:

1. Requesting data in a specific format from a provider
1. Describing content negotiation
1. How a provider would respond if it couldn't find the data you wanted

You could then build on that with integration tests:

1. Checking what happens if the provider simply isn't there
1. Network failures
1. Timeouts
1. Missing end points
1. Badly formed responses

## Help!
Depending on the kind of help or support you need, there are a few places you can try:

### I have a Scala-Pact feature request or bug to report
Have a look around our [issues page](https://github.com/ITV/scala-pact/issues) to see if anyone else has raised a similar request. If not, please raise a new issue and we'll try and help.

If you're a developer and you're feeling brave and generous, feel free to submit a pull request and we'll always do our best to accept it. For more information, please read our [contributing](https://github.com/ITV/scala-pact/blob/master/CONTRIBUTING.md) file.

### I'm looking for general information on Pact or CDC
Good places to start are the official [pact.io](http://www.pact.io) website or this article on [CDC](http://martinfowler.com/articles/consumerDrivenContracts.html).

### I have a question about using Scala-Pact or Pact
Please direct questions to the official [Pact support google group](https://groups.google.com/forum/#!forum/pact-support). As well as queries, we would love to hear feedback about your experiences with Scala-Pact and Pact generally.

If you're a developer looking to contribute or build a new Pact implementation there's a [group](https://groups.google.com/forum/#!forum/pact-dev) for that too!

## Pact Specification Compliance Level
Currently ScalaPact is not 100% compliant with the official Pact specification. We plan to be but the library is still under active development. The roadmap to Pact compliance will be something like:

1. Complete testing all tools against the official specification test cases. The only area of the specification that is believed to be incomplete is around the JSON body matching rules.
1. Consolidate our processes with the official implementor's guide.

Our intention is to eventually meet version 2 of the pact specification.

## Motivation
[Pact](https://github.com/realestate-com-au/pact) is an implementation of CDC testing ([Consumer Driven Contract testing](http://martinfowler.com/articles/consumerDrivenContracts.html)). There are other implementations like [Pacto](https://github.com/thoughtworks/pacto) and they vary slightly in how they interpret the testing process.

Following the Pact interpretation of CDC, the process goes something like this:

1. Write a piece of client code in a project that consumes a service and knows the details of how to connect to that provider.
1. Write a real integration test for that client code that hits a mock, but also as a side effect emits a JSON file describing the relationship. This JSON file is your Pact contract file. Note that in Pact (not Pacto) it is owned by the consumer.
1. During development of the consumer, the Pact file can be used to run a lightweight stub service that mimics the expected behavior of the provider. *Note: The provider need not even exist yet and the Pact files can form part of the providers design specification.*
1. Give the generated Pact file to the team that build the provider, an upstream service you depend on. This Pact file tells the provider team both how you expect their API to behave and *which parts of their API are delivering value to you the consumer*.
1. The provider then verifies the Pact by running the requests and responses described in the Pact file against their system.

Most of the original tools for CDC testing have come out of the Ruby community but a full list of officially supported languages can be found on the [pact.io](http://docs.pact.io/) website.

The crucial point to appreciate is that the client code is written in your native language using your normal test framework, and therefore, your Pact integrations tests must also be written in your native language too!

The other uses of the Pact files, verification and stubbing are largely standalone processes that are language agnostic and so it's quite feasible to use the Ruby implementations if you prefer.

There is another implementation of the Pact integration test suite that is compatible with Scala and [Specs2](https://etorreborre.github.io/specs2/) called [Pact-JVM](https://github.com/DiUS/pact-jvm). Pact-JVM also supports other JVM languages like Java and Groovy. Scala-Pact has the same aims and adheres to the same standards but attempts to deliver a more Scala specific experience.

## Scala Project Library Dependencies
The Pact integration test library itself depends on a range of Scala/Java libraries.

### Http4s
[Http4s](http://http4s.org/) powers ScalaPact's stubber.

### Argonaut
[Argonaut](http://argonaut.io/) is used to read and write the JSON Pact files.

### WireMock
[WireMock](http://wiremock.org/) is used to supply the mocks that ScalaPact runs the integration tests against.

### ScalaTest
[ScalaTest](http://www.scalatest.org/) is both our test suite of choice and the target for the ScalaPact implementation.

### Scalaj-Http
[Scalaj-Http](https://github.com/scalaj/scalaj-http) is used for quick synchronous HTTP calls.

### Scalaz
[Scalaz](https://github.com/scalaz/scalaz) is the glue that holds it all together.
