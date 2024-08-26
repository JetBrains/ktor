/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

repositories {
    google()
}

plugins {
    id("com.android.library")
    id("kotlin-android-extensions")
}

kotlin {
    android {
        publishAllLibraryVariants()
    }

    sourceSets {
        val androidMain by getting {
            kotlin.srcDir("android/src")
            dependencies {
                implementation(project(":ktor-client:ktor-client-plugins:ktor-client-tracing"))
                implementation(project(":ktor-client:ktor-client-core"))
                implementation("com.facebook.stetho:stetho:$android_stetho_version")
            }
        }

        val androidTest by getting {
            kotlin.srcDir("android/test")
            dependencies {
                implementation(project(":ktor-client:ktor-client-cio"))
                implementation(kotlin("test-junit5"))
                implementation("org.mockito:mockito-core:5.12.0")
            }
        }
    }

    sourceSets.all {
    }
}

android {
    compileSdkVersion(29)
    packagingOptions {
        exclude("META-INF/kotlinx-coroutines-core.kotlin_module")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    defaultConfig {
        minSdkVersion(9)
        targetSdkVersion(29)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
    useLibrary("android.test.mock")

    sourceSets {
        val main by getting {
            manifest.srcFile("AndroidManifest.xml")
        }
    }
}
