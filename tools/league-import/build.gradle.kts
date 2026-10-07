// The league importer: psmf.cz -> composeApp/.../files/leagues/*.json.
//
// JVM-only. Declared as a Kotlin Multiplatform module with a single jvm()
// target so that it uses the plugin the catalogue already has, rather than
// adding one: the only line this tool added to libs.versions.toml is jsoup.
//
// It depends on :shared so that it writes through the app's own seed DTOs
// and keeps ids with SeedIdentity -- the file format has one definition, and
// the importer cannot drift from what the app reads.
//
// jsoup lives here and nowhere else. The app stays without network.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(
        libs.versions.jvmToolchain
            .get()
            .toInt(),
    )

    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.jsoup)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            // SeedFileReader is suspend: the importer loads its own output
            // through the app's SeedLeagueCatalog before writing a byte.
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// `./gradlew :league-import:importLeague` -- a full run.
//
// Polite by construction: one request at a time, at least a second apart,
// a user-agent naming the project, and every page cached under
// `tools/league-import/cache/` (git-ignored) so a re-run during development
// does not touch the site. `-PimportArgs="--offline"` refuses to fetch at
// all and works from the cache alone.
tasks.register<JavaExec>("importLeague") {
    group = "psmf"
    description = "Imports league 6 from psmf.cz into the bundled seed files, keeping every existing id."
    classpath = files(tasks.named("jvmJar"), configurations.named("jvmRuntimeClasspath"))
    mainClass.set("cz.hspinovace.psmf.tools.leagueimport.MainKt")
    workingDir = rootDir
    args(
        (project.findProperty("importArgs") as String?)
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            .orEmpty(),
    )
}
