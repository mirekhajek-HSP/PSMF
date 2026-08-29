package cz.hspinovace.psmf.data.seed

import cz.hspinovace.psmf.resources.Res
import org.jetbrains.compose.resources.MissingResourceException

/**
 * Reads seed files out of Compose resources.
 *
 * This lives in `composeApp` rather than `shared` because Compose
 * resources are generated into this module and `shared` cannot see them.
 * It is the only thing in the seed-loading path that is not testable
 * without a device, which is exactly why it does nothing but fetch bytes.
 */
class ComposeResourceSeedFileReader : SeedFileReader {
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    override suspend fun read(fileName: String): String? =
        try {
            Res.readBytes("${SeedLeagueCatalog.DIRECTORY}/$fileName").decodeToString()
        } catch (_: MissingResourceException) {
            // A named-but-absent file is a data error the catalog reports as a
            // SeedProblem, not a crash.
            null
        }
}
