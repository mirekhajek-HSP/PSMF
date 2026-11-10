package cz.hspinovace.psmf.tools.leagueimport

/**
 * One league of one competition as psmf.cz files it, and what the app calls its groups.
 *
 * **Hanspaulská liga keeps every name and id it always had**: `6-k` is the group `6k`,
 * "6. liga K", report code `6K`. Any other competition's groups carry a prefix of their
 * own, so that its `1-a` can never be taken for Hanspaulská liga's 1. liga A: not by the
 * id, not on a fixture row, and not in the league select, which reads the level from the
 * report code's leading digits and so leaves `UVL 1A` outside the numbered hierarchy, as
 * `Group.leagueLevel` intends for the parallel competitions (DECISIONS 2026-11-10).
 */
data class Competition(
    /** `/souteze/2026-hanspaulska-liga-podzim/`. */
    val seasonPath: String,
    /** The league's number on psmf.cz: its groups are `<league>-a`, `<league>-b`, ... */
    val league: Int,
    val seasonId: String,
    val seasonName: String,
    /** Before the group's id and file name: empty for Hanspaulská liga. */
    val idPrefix: String = "",
    /** Before the group's number and letter in its name; empty gives "6. liga K". */
    val title: String = "",
    /** Before the number and letter in the report code; empty gives `6K`. */
    val codePrefix: String = "",
) {
    /** `6-k` -> `6k`; `1-a` -> `uvl1a`. */
    fun groupId(slug: String): String = idPrefix + slug.replace("-", "")

    fun groupName(slug: String): String {
        val number = numberOf(slug)
        val letter = letterOf(slug)
        return if (title.isEmpty()) "$number. liga $letter" else "$title $number$letter"
    }

    fun reportCode(slug: String): String = codePrefix + numberOf(slug) + letterOf(slug)

    /** Whether a group already in the files is one of this league's: a player only moves within one. */
    fun holds(groupId: String): Boolean = Regex(Regex.escape(idPrefix) + league + "[a-z]+").matches(groupId)

    companion object {
        val HANSPAULSKA_6 =
            Competition(
                seasonPath = "/souteze/2026-hanspaulska-liga-podzim/",
                league = 6,
                seasonId = "2026-podzim",
                seasonName = "Hanspaulská liga podzim 2026",
            )

        /** For the PSMF demo at Mikulovka, 12 November 2026 (DECISIONS 2026-11-10). 2 x 30, as Hanspaulská liga. */
        val ULTRAVETERANSKA_1 =
            Competition(
                seasonPath = "/souteze/2026-ultraveteranska-liga-podzim/",
                league = 1,
                seasonId = "2026-podzim-uvl",
                seasonName = "Ultraveteránská liga podzim 2026",
                idPrefix = "uvl",
                title = "Ultraveteránská liga",
                codePrefix = "UVL ",
            )

        /** What `--competition` takes. */
        val BY_NAME: Map<String, Competition> = mapOf("hl6" to HANSPAULSKA_6, "uvl1" to ULTRAVETERANSKA_1)

        private fun numberOf(slug: String): String = slug.substringBefore('-')

        private fun letterOf(slug: String): String = slug.substringAfter('-').uppercase()
    }
}
