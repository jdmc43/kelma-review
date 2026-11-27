import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

val javafxPlatform = when {
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) ->
        if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "mac-aarch64" else "mac"
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "win"
    System.getProperty("os.arch") in setOf("aarch64", "arm64") -> "linux-aarch64"
    else -> "linux"
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "tech.kelma.app.shared")
        }
    }
    
    jvm()
    
    android {
       namespace = "tech.kelma.app.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.androidDriver)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.nativeDriver)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.openjfx:javafx-base:${libs.versions.javafx.get()}:$javafxPlatform")
            implementation("org.openjfx:javafx-controls:${libs.versions.javafx.get()}:$javafxPlatform")
            implementation("org.openjfx:javafx-graphics:${libs.versions.javafx.get()}:$javafxPlatform")
            implementation("org.openjfx:javafx-media:${libs.versions.javafx.get()}:$javafxPlatform")
            implementation("org.openjfx:javafx-swing:${libs.versions.javafx.get()}:$javafxPlatform")
            implementation("org.openjfx:javafx-web:${libs.versions.javafx.get()}:$javafxPlatform")
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.sqliteDriver)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.compose.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
        commonMain.dependencies {
            implementation("tech.kelma:kelma-fsrs-v6:0.2.0-SNAPSHOT")
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
            implementation(libs.zstd.kmp.okio)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("KelmaDatabase") {
            packageName.set("tech.kelma.db")
        }
    }
}


tasks.withType<Test>().configureEach {
    systemProperty("tech.kelma.disable-native-card-renderer", "true")
}
