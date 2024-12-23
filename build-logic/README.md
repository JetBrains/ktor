# build-logic

Build logic shared between Ktor subprojects.

This is similar to `buildSrc`, but uses [composite builds](https://docs.gradle.org/current/userguide/composite_builds.html)
to prevent projects from becoming out-of-date on any change in `buildSrc`.

This project should be included in the root `settings.gradle.kts`:

`<root project dir>/settings.gradle.kts`
```kotlin
includeBuild("build-logic")
```

`<root project dir>/build.gradle.kts`
```kotlin
plugins {
    id("ktorbuild.base")
}
```

*The structure of this project is inspired by the structure used in [Dokka](https://github.com/Kotlin/dokka/tree/v2.0.0/build-logic/src/main/kotlin) and [Gradle](https://github.com/gradle/gradle/tree/v8.12.0/build-logic/jvm/src/main/kotlin).*
