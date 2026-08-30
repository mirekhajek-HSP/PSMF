package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.data.match.MatchRepository
import cz.hspinovace.psmf.domain.Match
import cz.hspinovace.psmf.domain.Official
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.RefereeAssignment

/**
 * What the header screen has collected so far.
 *
 * Deliberately *not* a domain type. [RefereeAssignment] describes a
 * complete header — it will not hold a blank delegating team, and it
 * cannot hold half a name — whereas this describes a form being filled in,
 * which is a different thing and is allowed to be nonsense. Keeping the
 * two apart is what lets the screen show a field-level error instead of
 * refusing to build a value.
 */
data class MatchHeaderEntry(
    val refereeName: String = "",
    val refereeLicensedHire: Boolean = false,
    val assistantName: String = "",
    val assistantLicensedHire: Boolean = false,
    /**
     * `Týmy` — **the team that delegated the referees.**
     *
     * A string rather than a `TeamId` because it genuinely is one:
     * substitute referees write *their own* team, which may not be in this
     * group at all (analysis section 2.5). The screen offers the group's
     * teams as taps and accepts anything else as text.
     */
    val delegatingTeam: String = "",
) {
    /** The assignment this entry describes, or null if it is not one yet. */
    fun toAssignment(): RefereeAssignment? {
        val main = PersonName.orNull(refereeName) ?: return null
        val team = delegatingTeam.trim().ifBlank { return null }
        // A blank assistant is legitimate -- the report has one referee in
        // plenty of matches. A non-blank one that is not a name is not.
        val assistant =
            if (assistantName.isBlank()) {
                null
            } else {
                PersonName.orNull(assistantName)?.let { Official(it, assistantLicensedHire) } ?: return null
            }
        return RefereeAssignment(
            main = Official(main, refereeLicensedHire),
            assistant = assistant,
            delegatingTeam = team,
        )
    }

    /**
     * What is still wrong, per field, in the order the screen shows them.
     *
     * A list rather than a boolean for the same reason [Match] reports its
     * problems as a list: the referee needs to know *which* field, at the
     * pitch, in the dark.
     */
    fun problems(): List<HeaderProblem> =
        buildList {
            when {
                refereeName.isBlank() -> add(HeaderProblem.RefereeNameMissing)
                PersonName.orNull(refereeName) == null -> add(HeaderProblem.RefereeNameNotLatin)
            }
            if (assistantName.isNotBlank() && PersonName.orNull(assistantName) == null) {
                add(HeaderProblem.AssistantNameNotLatin)
            }
            if (delegatingTeam.isBlank()) add(HeaderProblem.DelegatingTeamMissing)
        }

    companion object {
        /** Rebuilds the form from a report that was already started. */
        fun from(assignment: RefereeAssignment?): MatchHeaderEntry =
            if (assignment == null) {
                MatchHeaderEntry()
            } else {
                MatchHeaderEntry(
                    refereeName = assignment.main.name.value,
                    refereeLicensedHire = assignment.main.licensedHire,
                    assistantName =
                        assignment.assistant
                            ?.name
                            ?.value
                            .orEmpty(),
                    assistantLicensedHire = assignment.assistant?.licensedHire == true,
                    delegatingTeam = assignment.delegatingTeam,
                )
            }
    }
}

/** A field of the header that is not yet usable, and why. */
sealed interface HeaderProblem {
    data object RefereeNameMissing : HeaderProblem

    /**
     * Names go to PSMF to be matched against a card cabinet that is in the
     * Latin alphabet. The app being readable in Ukrainian does not change
     * what gets written on the report.
     */
    data object RefereeNameNotLatin : HeaderProblem

    data object AssistantNameNotLatin : HeaderProblem

    /**
     * The single most expensive field on page 1 to leave blank: the fine
     * for a bad report is charged to this team (analysis section 2.5,
     * rule 10).
     */
    data object DelegatingTeamMissing : HeaderProblem
}

/**
 * Writes the header through on every keystroke that produces a usable one.
 *
 * Saves nothing while the entry is incomplete, because there is nothing
 * complete to save: [RefereeAssignment] is all-or-nothing by construction.
 * The window in which work can be lost is therefore a partly-typed name
 * before a delegating team has been picked, which is seconds and two
 * words. Everything after this screen writes through unconditionally.
 */
class SaveMatchHeader(
    private val matches: MatchRepository,
) {
    suspend operator fun invoke(
        match: Match,
        entry: MatchHeaderEntry,
    ): Match {
        val assignment = entry.toAssignment() ?: return match
        val updated = match.copy(officials = assignment)
        matches.save(updated)
        return updated
    }
}
