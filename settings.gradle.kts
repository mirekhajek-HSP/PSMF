// pluginManagement must be the first block in this file; Gradle resolves
// plugins before it evaluates anything else.
//
// The repositories are deliberately unfiltered. Narrowing google() with
// mavenContent { includeGroupAndSubgroups(...) } looks tidy and breaks the
// build: the Android plugin pulls org.jetbrains:annotations:23.0.0 onto
// the buildscript classpath, which then collides with the annotations 13.0
// that Gradle pins to its embedded Kotlin, and resolution fails with
// "Pinned to the embedded Kotlin". Leave them broad.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "psmf-app"

include(":shared")
include(":composeApp")
include(":androidApp")
