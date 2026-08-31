import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    // expect/actual *classes* are still flagged Beta, and the driver
    // factory is a legitimate use: Android needs a Context to construct
    // one and iOS does not. The warning is expected, so silence it rather
    // than let real warnings hide behind it.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // AGP 9's KMP library target, from com.android.kotlin.multiplatform.library.
    // It replaces androidTarget {} plus a separate top-level android {}
    // block: the Android configuration is now part of the Kotlin target.
    //
    // NOTE: the JetBrains migration guide still says `androidLibrary {}`.
    // That spelling is deprecated as of AGP 9.3; the block is `android {}`
    // nested inside `kotlin {}`.
    android {
        namespace = "cz.hspinovace.psmf.shared"
        // Pinned to what the container image already installs. Left to its
        // own default, AGP asks sdkmanager for a different build-tools
        // package and re-downloads it on every single container run,
        // because `docker compose run --rm` discards the writable layer.
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

        // WITHOUT THIS THERE IS NO ANDROID TEST COMPILATION AT ALL.
        //
        // The KMP library plugin creates host-test and device-test
        // compilations only on request. Until this line existed, a test in
        // src/androidUnitTest/ was not compiled, not run and not reported:
        // it did not fail, it did not pass, it simply was not there. A
        // planted `fail()` came back green, which is the same failure mode
        // as a JUnit 4 test on a JUnit 5 platform.
        //
        // sourceSetTreeName = "test" is what names the source set
        // `androidUnitTest` and puts it under commonTest, so the shared
        // suite runs on the Android target too rather than only on the JVM.
        withHostTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            isReturnDefaultValues = true
        }
    }

    // The fast test loop. CLAUDE.md documents `:shared:jvmTest` as the
    // inner loop, and the Stop hook runs exactly that.
    jvm()

    // Declared so the shared module is genuinely multiplatform from the
    // start. They cannot be BUILT on Linux; the Kotlin plugin disables
    // their compile tasks on a non-macOS host and the build skips them.
    //
    // iosX64 (the Intel simulator) is deliberately absent: Compose
    // Multiplatform and lifecycle no longer publish an ios_x64 variant,
    // so declaring it fails dependency resolution. Device is arm64 and
    // the simulator is arm64 on Apple Silicon.
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
        jvmTest.dependencies {
            // Named explicitly, not inherited: SchemaMigrationTest builds a
            // driver with NO schema on purpose, to write rows that predate a
            // migration, and that is not something the app's own factory can
            // or should be able to do.
            implementation(libs.sqldelight.driver.jvm)
        }
    }
}

sqldelight {
    databases {
        create("PsmfDatabase") {
            packageName.set("cz.hspinovace.psmf.db")

            // Where the recorded schema of each released version lives.
            //
            // Setting it is what registers generateCommonMainPsmfDatabaseSchema
            // at all -- with no directory the plugin has nowhere to write, so
            // the task simply does not appear and `gradlew tasks` gives no hint
            // that migration verification is running against nothing.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))

            // Makes `check` depend on verifyCommonMainPsmfDatabaseMigration,
            // which applies every .sqm to every recorded .db and compares the
            // result against the .sq files. Verification nobody has to
            // remember to run is the only kind that stays true.
            verifyMigrations.set(true)
        }
    }
}

// The recorded schema files are test data: SchemaMigrationTest starts from
// the bytes of a released schema rather than from an empty database. Passed
// as a property rather than resolved from the working directory, which is
// not the same for every test task.
tasks.withType<Test>().configureEach {
    systemProperty(
        "psmf.schemaDirectory",
        layout.projectDirectory
            .dir("src/commonMain/sqldelight/databases")
            .asFile.absolutePath,
    )
}
