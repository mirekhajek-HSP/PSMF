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

    // `expect object LocalAppLocale` is an expect *class*, which is still
    // flagged Beta. It is the shape JetBrains document for changing the
    // app's language without restarting the activity, and matching the
    // documented sample is worth more here than avoiding the warning --
    // the next person to read it can diff it against the page it came
    // from. Silenced so real warnings are not hidden behind it.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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

    // Where Compose UI tests run. Not a shipped desktop app: composeApp
    // targets Android and iOS, so without this a UI test could only run on
    // a device or a Mac -- neither of which the build container can reach,
    // which would leave every screen untested until someone plugged a
    // phone in. `runComposeUiTest` on this target runs in the sandbox.
    jvm()

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
            // System back. A separate artifact from compose.ui, and there
            // is no compose.* accessor for it.
            implementation(libs.compose.ui.backhandler)

            // The fixture list formats dates itself. `shared` depends on
            // kotlinx-datetime with `implementation`, so it does not leak.
            implementation(libs.kotlinx.datetime)

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
            // FileProvider, for the exported files.
            implementation(libs.androidx.core.ktx)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)

            // The multiplatform Compose test API. Still experimental
            // upstream; it is the only way to drive a composable without a
            // platform test runner, so the opt-in is the price.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        jvmTest.dependencies {
            // Skiko and the desktop windowing bits that back the test host.
            implementation(compose.desktop.currentOs)
        }
    }
}

// The bundled fonts are test data for BundledFontTest, which reads their
// cmap tables rather than trusting that a face said to have Cyrillic has it.
// A property rather than a working directory, which is not the same for
// every test task.
tasks.withType<Test>().configureEach {
    systemProperty(
        "psmf.fontDirectory",
        layout.projectDirectory
            .dir("src/commonMain/composeResources/font")
            .asFile.absolutePath,
    )
}

compose.resources {
    publicResClass = true
    packageOfResClass = "cz.hspinovace.psmf.resources"
    generateResClass = ResourcesExtension.ResourceClassGeneration.Always
}
