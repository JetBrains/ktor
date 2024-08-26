# How to contribute

Before we get started, thank you for considering contributing to Ktor. It's awesome of you!

There are multiple ways you can contribute:

* Code
* Documentation
* Community Support
* Feedback/Issue reports

Independently of how you'd like to contribute, please make sure you read and comply with the [Code of Conduct](CODE_OF_CONDUCT.md).

## Code

There are many bugs and features in the Ktor backlog and you're free to pick any of them. We do recommend however starting
with some of the [low hanging fruit](https://youtrack.jetbrains.com/issues?q=%23Ktor%20%20%20%23%7BUp%20For%20Grabs%7D%20%20%23Unresolved%20).

### Building the project

Ktor is built with Gradle. Given it is multiplatform, you can build Ktor for the JVM, Native, and JavaScript.

To build the projects and produce the corresponding artifacts, use

`./gradlew assemble`

to run tests use

`./gradlew jvmTest` which runs all tests on the JVM. This is the minimum required for testing. If writing code
for other platforms, the corresponding tests for these should also be run. To see the list of tasks use

`./gradlew tasks`

For Ktor to build correctly, a series of additional libraries/tools need to be installed, based on the operating
system you're using for development:

**Linux**

Run the following commands to install `libcurl` and `libncurses`:

```
        sudo apt-get update
        sudo apt-get install libncurses5 libncursesw5 libtinfo5
        sudo apt-get install libcurl4-openssl-dev
```

**macOS** 

The easiest way to install `libcurl` and 'libncurses` on macOS is to use [Homebrew](https://brew.sh). Run the following commands:

```
    brew install curl
    brew install ncurses
```

If targeting macOS and/or iOS, install `Xcode` and `Xcode command line tools` on macOS.

**Windows**

For development on Windows, it is recommended to use [Cygwin](http://cygwin.com/) which will provide the necessary
libraries such as `libncurses`.

#### Referencing artifacts locally

There are two ways to reference artifacts from the development Ktor locally in another project, which is usually
used for debugging purposes. One of these is to publish to [Maven Local](https://docs.gradle.org/current/userguide/publishing_maven.html). The other
(and somewhat simpler), is to use the `includeBuild` functionality of Gradle. Reference the Ktor project from your sample project
by adding the following line to your `settings.gradle(.kts)` file:

```groovy
    includeBuild("/PATH/TO/KTOR")
```

#### Importing into IntelliJ IDEA

To import into IntelliJ IDEA, just open up the `Ktor` project folder. IntelliJ IDEA should automatically detect
that it is a Gradle project and import it. It's important that you make sure that all building and test operations
are delegated to Gradle under [Gradle Settings](https://www.jetbrains.com/help/idea/gradle-settings.html).

### Pull Requests

Contributions are made using Github [pull requests](https://help.github.com/en/articles/about-pull-requests):

1. Fork the Ktor repository and work on your fork.
2. [Create](https://github.com/ktorio/ktor/compare) a new PR with a request to merge to the **main** branch.
3. Ensure that the description is clear and refers to an existing ticket/bug if applicable, prefixing the description with
   KTOR-{NUM}, where {NUM} refers to the YouTrack issue.
4. When contributing a new feature, provide motivation and use-cases describing why
   the feature not only provides value to Ktor, but also why it would make sense to be part of the Ktor framework itself.
5. If the contribution requires updates to documentation (be it updating existing contents or creating new one), please
   file a new ticket on [YouTrack](https://youtrack.jetbrains.com/issues/KTOR).
6. Make sure any code contributed is covered by tests and no existing tests are broken.

### Style guides

A few things to remember:

* Your code should conform to
  the official [Kotlin code style guide](https://kotlinlang.org/docs/reference/coding-conventions.html)
  except that star imports should be always enabled
  (ensure Preferences | Editor | Code Style | Kotlin, tab **Imports**, both `Use import with '*'` should be checked).
* Every new source file should have a copyright header.
* Every public API (including functions, classes, objects and so on) should be documented,
  every parameter, property, return types and exceptions should be described properly.
* A questionable and new API should be marked with the `@KtorExperimentalAPI` annotation.
* A Public API that is not intended to be used by end-users that couldn't be made private/internal due to technical reasons,
  should be marked with `@InternalAPI` annotation.

### Commit messages

* Commit messages should be written in English
* They should be written in present tense using imperative mood ("Fix" instead of "Fixes", "Improve" instead of "Improved").
  Add the related bug reference to a commit message (bug number after a hash character between round braces).
* When applicable, prefix the commit message with KTOR-{NUM} where {NUM} represents the YouTrack issue number

See [How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/)

### Design process

Whether you're thinking of a new feature or want to change the design of an existing process, before making any
code contributions, please make sure you read how we handle the [design process on the team](https://blog.jetbrains.com/ktor/2020/09/24/ktor-design-process/).

## Documentation

Ktor documentation is placed in a separate [ktor-documentation](https://github.com/ktorio/ktor-documentation) repository. See the [Contributing](https://github.com/ktorio/ktor-documentation#contributing) section to learn how you can contribute to Ktor docs.

## Community Support

Ktor provides a number of [channels for support](https://ktor.io/support). In addition to our support engineers, we also count
on our community to help, without whom Ktor wouldn't be where it is today. If you'd like to help others, please join one of our community
channels and help out. It's also a great way to learn!

## Feedback/Issue Reports

Please use [YouTrack](https://youtrack.jetbrains.com/issues/KTOR) to submit issues, whether these are
bug reports or feature requests. Before doing so however, please take into consideration the following:

* Search for existing issues to avoid reporting duplicates.
* When submitting a bug report:
    * Test it against the most recently released version. It might have been already fixed.
    * Indicate the platform the issue relates to (JVM, Native, JavaScript), along with the operating system.
    * Include the code that reproduces the problem. Provide the complete reproducer code, yet minimize it as much as possible.
      If you'd like to write a unit test to reproduce the issue, even better. We love tests! However, don't be put off reporting any weird or rarely appearing issues just because you cannot consistently
      reproduce them.
    * If it's a behavioural bug, explain what behavior you've expected and what you've got.
* When submitting a feature request:
    * Explain why you need the feature &mdash; what's your use-case, what's your domain. Explaining the problem you face is more important than suggesting a solution.
      Report your problem even if you don't have any proposed solution. If there is an alternative way to do what you need, then show the code of the alternative.
      

