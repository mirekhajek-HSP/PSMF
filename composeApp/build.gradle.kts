import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    android {
        // Distinct from :androidApp's cz.hspinovace.psmf. Two modules in
        // one APK may not share a namespace -- their R classes would
        // collide. The Kotlin package is unaffected and stays
        // cz.hspinovace.psmf.
        namespace = "cz.hspinovace.psmf.composeapp"
        // See the note in shared/build.gradle.kts.
        buildToolsVersion = libs.versions.androidBuildTools.get()
        compileSdk =
            libs.versions.androidCompileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.androidMinSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        // Compose Multiplatform packages its resources through the Android
        // resource pipeline, so this module genuinely has Android
        // resources even though it contains no res/ directory.
        androidResources {
            enable = true
        }
    }

    // Declared so the iOS wrapper in iosApp/ has a framework to link
    // against. Cannot be built on Linux; that is a macOS session.
    // iosX64 is omitted -- see the note in shared/build.gradle.kts.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            // Only for the actual of platformModule(), which needs
            // androidContext(). The Activity and Application are in
            // :androidApp.
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "cz.hspinovace.psmf.resources"
    generateResClass = ResourcesExtension.ResourceClassGeneration.Always
}
