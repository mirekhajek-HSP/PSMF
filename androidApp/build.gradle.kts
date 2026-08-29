import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Android entry point, and nothing else.
//
// It exists as a separate module because AGP 9 will not apply
// com.android.application alongside org.jetbrains.kotlin.multiplatform in
// one subproject. Everything shared -- the Compose UI, the DI graph, the
// domain -- lives in :composeApp and :shared, which are multiplatform.
// What is left here is genuinely Android-only: an Application class, an
// Activity, a manifest and the launcher resources.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "cz.hspinovace.psmf"

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
        // NOT FINAL. This becomes permanent the moment the app is
        // published, and docs/TECH_STACK.md section 5 still lists it as
        // open. Confirm it against the company convention before any
        // store upload; golblok uses cz.hsp.footballmatch.
        applicationId = "cz.hspinovace.psmf"

        minSdk =
            libs.versions.androidMinSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.androidTargetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            // Release signing happens outside this workspace. No keystore
            // is present in the container and none should ever be.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.composeApp)

    implementation(compose.runtime)
    implementation(libs.androidx.activity.compose)

    // PsmfApplication starts Koin with androidContext(); that lives here
    // rather than in :composeApp because starting the graph is the
    // application's job, not the shared UI's.
    implementation(libs.koin.android)
}
