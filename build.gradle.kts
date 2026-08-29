// Gradle pins `org.jetbrains:annotations` to 13.0 on the buildscript
// classpath, because that is what its own embedded Kotlin stdlib uses. The
// Android plugin drags in 23.0.0 through ddmlib and layoutlib-api, and the
// two collide the moment SQLDelight's plugin and the Compose compiler
// plugin are both present, failing with "Pinned to the embedded Kotlin".
//
// Verified by bisection: AGP alone, AGP + Compose, and AGP + SQLDelight
// all resolve; AGP + SQLDelight + the Compose compiler plugin does not.
// SQLDelight 2.1.0 also avoids it, but freezing a dependency for a reason
// nobody will remember is worse than one line that says why.
//
// Nothing on the classpath actually needs annotations 13.0 at runtime;
// 23.0.0 is backwards compatible.
buildscript {
    configurations.classpath {
        resolutionStrategy {
            force("org.jetbrains:annotations:23.0.0")
        }
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))

        // The baseline is committed and EMPTY, and is meant to stay that
        // way. On a green field nothing needs suppressing; an entry
        // appearing here should be a deliberate, reviewed decision rather
        // than the quiet result of a failing build.
        baseline = rootProject.file("config/detekt/baseline.xml")

        // detekt defaults to the JVM layout (src/main/kotlin), which finds
        // nothing at all in a multiplatform module. Every source set has to
        // be named explicitly or `detekt` passes by analysing zero files.
        source.setFrom(
            "src/commonMain/kotlin",
            "src/commonTest/kotlin",
            "src/androidMain/kotlin",
            // :androidApp is a plain Android module and uses the JVM layout.
            "src/main/kotlin",
            "src/androidUnitTest/kotlin",
            "src/iosMain/kotlin",
            "src/jvmMain/kotlin",
            "src/jvmTest/kotlin",
        )
    }

    ktlint {
        version.set(rootProject.libs.versions.ktlint)
        // Style comes from .editorconfig so that the Gradle plugin and the
        // ktlint CLI used by the PostToolUse hook cannot disagree.
        filter {
            exclude { it.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
}
