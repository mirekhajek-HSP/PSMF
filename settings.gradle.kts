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

// Reads league data from psmf.cz into the seed files. A JVM-only tool, not
// part of the app: it is the one place jsoup and a network call are allowed
// (DECISIONS 2026-10-07). Kept under tools/ so nobody mistakes it for a
// module the app ships.
include(":league-import")
project(":league-import").projectDir = file("tools/league-import")
