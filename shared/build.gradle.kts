import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // The fast test loop. CLAUDE.md documents `:shared:jvmTest` as the
    // inner loop, and the Stop hook added in Phase 2 runs exactly that.
    jvm()

    // Declared so the shared module is genuinely multiplatform from the
    // start. They cannot be BUILT on Linux; the Kotlin plugin disables
    // their compile tasks on a non-macOS host and the build skips them.
    //
    // iosX64 (the Intel simulator) is deliberately absent: Compose
    // Multiplatform 1.12.0 and lifecycle 2.11.0 no longer publish an
    // ios_x64 variant, so declaring it fails dependency resolution.
    // Device is arm64 and the simulator is arm64 on Apple Silicon.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            api(libs.koin.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.driver.native)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.jvm)
        }
    }
}

android {
    namespace = "cz.hspinovace.psmf.shared"
    // Pinned to what the container image already installs. Left to its
    // own default, AGP asks sdkmanager for a different build-tools
    // package and re-downloads it on every single container run, because
    // `docker compose run --rm` discards the writable layer each time.
    buildToolsVersion = libs.versions.androidBuildTools.get()
    compileSdk =
        libs.versions.androidCompileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.androidMinSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("PsmfDatabase") {
            packageName.set("cz.hspinovace.psmf.db")
        }
    }
}
