package cz.hspinovace.psmf.data.seed

/**
 * Reads a seed file by name from wherever seed files happen to live.
 *
 * `shared` owns the parsing and the mapping to domain types; it does not
 * own *where the bytes come from*. On device that is Compose resources,
 * which only `composeApp` can reach; in tests it is an in-memory map. An
 * interface here is what keeps the whole loader testable without a device
 * and without a platform type in `commonMain`.
 */
fun interface SeedFileReader {
    /** Returns the file's contents, or null if there is no such file. */
    suspend fun read(fileName: String): String?
}

/** Everything that can go wrong loading seed data, as values rather than exceptions. */
sealed interface SeedProblem {
    val fileName: String

    data class FileMissing(
        override val fileName: String,
    ) : SeedProblem

    data class Unparseable(
        override val fileName: String,
        val detail: String,
    ) : SeedProblem

    data class InconsistentData(
        override val fileName: String,
        val detail: String,
    ) : SeedProblem
}

/**
 * Carries a [SeedProblem] out of the loader.
 *
 * [cause] is kept deliberately: the underlying serialization error names
 * the offending field and line, and someone hand-editing a group file
 * needs that far more than they need a tidy message.
 */
class SeedException(
    val problem: SeedProblem,
    cause: Throwable? = null,
) : Exception("Seed data problem in ${problem.fileName}: $problem", cause)
