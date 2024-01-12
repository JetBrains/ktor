<div align="center">

  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/ktorio/ktor/main/.github/images/ktor-logo-for-dark.svg">
    <img alt="Ktor logo" src="https://raw.githubusercontent.com/ktorio/ktor/main/.github/images/ktor-logo-for-light.svg">
  </picture>

</div>

[![Official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Maven Central](https://img.shields.io/maven-central/v/io.ktor/ktor)](https://mvnrepository.com/artifact/io.ktor)
[![Kotlin](https://img.shields.io/badge/kotlin-1.8.22-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Slack channel](https://img.shields.io/badge/chat-slack-green.svg?logo=slack)](https://kotlinlang.slack.com/messages/ktor/)
[![GitHub License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Contribute with Gitpod](https://img.shields.io/badge/Contribute%20with-Gitpod-908a85?logo=gitpod)](https://gitpod.io/#https://github.com/ktorio/ktor)

Ktor is an asynchronous framework for creating microservices, web applications and more. Written in Kotlin from the
ground up.

First add the dependency to your project:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-netty:$ktor_version")
}
```

Then create an `Application` and install some features:

```kotlin
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.engine.*

fun main(args: Array<String>) {
    embeddedServer(Netty, 8080) {
        routing {
            get("/") {
                call.respondText("Hello, world!", ContentType.Text.Html)
            }
        }
    }.start(wait = true)
}
```

You also can use [Ktor Gradle Plugin](https://github.com/ktorio/ktor-build-plugins) to configure bom, run tasks and deployment:
```kotlin
plugins {
    id("io.ktor.plugin") version "2.3.4"
}

dependencies {
    implementation("io.ktor:ktor-server-netty")
}
```

To run the created application, execute:
```shell
./gradlew run
```

* Runs embedded web server on `localhost:8080`
* Installs routing and responds with `Hello, world!` when receiving a GET http request for the root path

## Start using Ktor

Build your first Kotlin HTTP or RESTful application using Ktor: [start.ktor.io](https://start.ktor.io)

## Principles

#### Unopinionated

Ktor Framework doesn't impose a lot of constraints on what technology a project is going to use – logging,
templating, messaging, persistence, serialization, dependency injection, etc.
Sometimes it may be required to implement a simple interface, but usually it is a matter of writing a
transforming or intercepting function. Features are installed into the application using a unified *interception*
mechanism
which allows building arbitrary pipelines.

Ktor Applications can be hosted in any servlet container with Servlet 3.0+ API support such as Tomcat, or
standalone using Netty or Jetty. Support for other hosts can be added through the unified hosting API.

Ktor APIs are mostly functions calls with lambdas. Thanks to Kotlin DSL capabilities, the code looks declarative.
Application composition is entirely up to the developer's choice – with functions or classes, using dependency injection
framework or doing it all manually in the main function.

#### Asynchronous

The Ktor pipeline machinery and API are utilising Kotlin coroutines to provide easy-to-use asynchronous
programming model without making it too cumbersome. All host implementations are using asynchronous I/O facilities
to avoid thread blocking.

#### Testable

Ktor applications can be hosted in a special test environment, which emulates a web server to some
extent without actually doing any networking. It provides easy way to test an application without mocking
too much stuff, and still achieve good performance while validating application calls. Running integration tests with a
real
embedded web server are of course possible, too.

## JetBrains Product

Ktor is an official [JetBrains](https://jetbrains.com) product and is primarily developed by the team at JetBrains, with
contributions
from the community.

## Documentation

Please visit [ktor.io](http://ktor.io) for Quick Start and detailed explanations of features, usage and machinery.

* Getting started with [Gradle](https://ktor.io/docs/gradle.html)
* Getting started with [Maven](https://ktor.io/docs/maven.html)
* Getting started with [IDEA](https://ktor.io/docs/intellij-idea.html)

## Reporting Issues / Support

Please use [our issue tracker](https://youtrack.jetbrains.com/issues/KTOR) for filing feature requests and bugs. If
you'd like to ask a question, we recommend [StackOverflow](https://stackoverflow.com/questions/tagged/ktor) where
members of the team monitor frequently.

There is also community support on the [Kotlin Slack Ktor channel](https://app.slack.com/client/T09229ZC6/C0A974TJ9)

## Reporting Security Vulnerabilities

If you find a security vulnerability in Ktor, we kindly request that you reach out to the JetBrains security team via
our [responsible disclosure process](https://www.jetbrains.com/legal/terms/responsible-disclosure.html).

## Inspirations

Kotlin web frameworks such as Wasabi and Kara, which are currently deprecated.

## Contributing

Please see [the contribution guide](CONTRIBUTING.md) and the [Code of conduct](CODE_OF_CONDUCT.md) before contributing.
