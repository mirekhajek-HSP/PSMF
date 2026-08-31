package cz.hspinovace.psmf.export

/**
 * The report as a spreadsheet.
 *
 * **This is the artefact the demo is actually selling.** The value to PSMF
 * is not a better referee experience, it is the elimination of a week of
 * transcription (analysis section 1) — and what replaces retyping is a
 * file their crew can open, not a prettier app.
 *
 * Two details decide whether that happens, and both are easy to get wrong:
 *
 * - **Semicolons, not commas.** Excel in a Czech locale uses `;` as the
 *   list separator. A comma-separated file opens as one column per row,
 *   and the crew goes back to retyping.
 * - **A UTF-8 byte-order mark.** Without it Excel reads the bytes as the
 *   system code page and every `ě`, `š` and `ř` arrives as mojibake.
 *
 * A ZoU is a document rather than a table, so this writes it as blocks —
 * each with its own header row, separated by blank lines, which is what
 * somebody would build by hand and what a spreadsheet opens cleanly.
 */
object ZouCsv {
    /**
     * Excel treats a leading BOM as "this file is UTF-8". Without it, a
     * Czech Windows machine reads CP1250 and the diacritics are ruined.
     *
     * Spelled as an escape on purpose. Written as the character itself it
     * is invisible, and an invisible character is one an editor, a
     * formatter or a copy can drop without anyone noticing — which is
     * exactly what happened once already.
     */
    const val BYTE_ORDER_MARK: String = "\uFEFF"

    /** The Czech-locale list separator. */
    const val SEPARATOR: String = ";"

    fun format(report: ZouReport): String =
        BYTE_ORDER_MARK +
            buildString {
                headerBlock(report)
                report.lineups.forEach { lineupBlock(it) }
                goalsBlock(report)
                cardsBlock(report.cards)
                resultBlock(report.result)
                assessmentBlock(report.assessment)
                confirmationsBlock(report)
            }.trimEnd() + "\r\n"

    private fun StringBuilder.headerBlock(report: ZouReport) {
        val header = report.header
        row(ZouWords.TITLE)
        row(ZouLabels.Header.PITCH, header.pitch)
        row(ZouLabels.Header.DATE, header.dateWritten)
        row(ZouLabels.Header.TIME, header.timeWritten)
        row(ZouLabels.Header.LEAGUE, header.league)
        row(ZouLabels.Assessment.HOME, header.homeTeam)
        row(ZouLabels.Assessment.AWAY, header.awayTeam)
        row(ZouLabels.Header.REFEREE, header.refereeWritten)
        row(ZouLabels.Header.ASSISTANT, header.assistantWritten.orEmpty())
        row(ZouLabels.Header.DELEGATING_TEAMS, header.delegatingTeam)
        blank()
    }

    private fun StringBuilder.lineupBlock(lineup: ZouLineup) {
        row("${ZouLabels.Lineup.TEAM_NAME} (${lineup.side.mark})", lineup.teamName)
        row(ZouLabels.Lineup.KIT_COLOUR, lineup.kitLabel)
        row(ZouLabels.Lineup.JERSEY_NUMBER, ZouLabels.Lineup.IDENTIFIER, ZouLabels.Lineup.PLAYER_NAME)
        lineup.rows.forEach { row(it.jerseyNumber?.toString().orEmpty(), it.identification, it.name) }
        blank()
    }

    private fun StringBuilder.goalsBlock(report: ZouReport) {
        row(ZouLabels.Goals.SECTION)
        row(
            "D/H",
            ZouLabels.Goals.TIME,
            ZouLabels.Goals.NUMBER,
            ZouLabels.Goals.SCORER,
            ZouLabels.Goals.SCORE_AFTER,
        )
        report.goals.forEach { goal ->
            row(
                goal.side.mark,
                goal.minute,
                goal.jerseyNumber?.toString().orEmpty(),
                // Blank rather than a dash: a spreadsheet cell that is empty
                // is easier to filter than one holding punctuation.
                goal.scorer.orEmpty(),
                goal.scoreAfter,
            )
        }
        blank()
    }

    private fun StringBuilder.cardsBlock(cards: ZouCards) {
        row(ZouLabels.Cards.SECTION)
        if (!cards.accountedFor) {
            row(ZouWords.NOT_GIVEN)
            blank()
            return
        }
        if (cards.noneIssued) {
            row(ZouLabels.Cards.NONE_ISSUED)
            blank()
            return
        }
        row("D/H", ZouLabels.Goals.TIME, ZouLabels.Goals.NUMBER, ZouLabels.Lineup.PLAYER_NAME, "Důvod", "Karta")
        cards.yellow.forEach { cardRow(it, YELLOW) }
        cards.red.forEach { cardRow(it, RED) }
        blank()
    }

    private fun StringBuilder.cardRow(
        card: ZouCard,
        colour: String,
    ) {
        row(
            card.side.mark,
            card.minute,
            card.jerseyNumber?.toString().orEmpty(),
            card.name,
            card.reason,
            colour,
        )
    }

    private fun StringBuilder.resultBlock(result: ZouResult?) {
        row(ZouLabels.Result.HALF_TIME, result?.halfTime.orEmpty())
        row(ZouLabels.Result.FINAL, result?.fullTime.orEmpty())
        row(ZouLabels.Result.WINNER, result?.winner.orEmpty())
        blank()
    }

    private fun StringBuilder.assessmentBlock(assessment: ZouAssessment) {
        row(
            "D/H",
            ZouLabels.Assessment.BEST_PLAYER,
            ZouLabels.Assessment.WAITING_TIME,
            ZouLabels.Assessment.SHIRTS_NUMBERED,
            ZouLabels.Assessment.UNIFORM_KIT,
        )
        teamAssessmentRow(ZouLabels.Assessment.HOME, assessment.home)
        teamAssessmentRow(ZouLabels.Assessment.AWAY, assessment.away)
        blank()
        row(ZouLabels.Assessment.COMMENTARY, assessment.commentary)
        blank()
    }

    private fun StringBuilder.teamAssessmentRow(
        mark: String,
        team: ZouTeamAssessment,
    ) {
        row(
            mark,
            team.bestPlayer?.toString().orEmpty(),
            team.waitingTimeMinutes.toString(),
            ZouWords.of(team.shirtsProperlyNumbered),
            ZouWords.of(team.uniformKitColour),
        )
    }

    private fun StringBuilder.confirmationsBlock(report: ZouReport) {
        listOf(ZouWords.HOME_CAPTAIN, ZouWords.AWAY_CAPTAIN, ZouWords.REFEREE).forEach { party ->
            row(
                party,
                report.confirmations
                    .firstOrNull { it.party == party }
                    ?.byWritten
                    .orEmpty(),
            )
        }
    }

    private fun StringBuilder.row(vararg cells: String) {
        append(cells.joinToString(SEPARATOR) { it.escaped() })
        append("\r\n")
    }

    private fun StringBuilder.blank() {
        append("\r\n")
    }

    /**
     * A cell containing the separator, a quote or a newline is quoted, and
     * embedded quotes are doubled. A free-text card reason or a 400-word
     * commentary will contain at least one of these sooner or later.
     */
    private fun String.escaped(): String =
        if (any { it in NEEDS_QUOTING }) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }

    /** A cell holding any of these has to be quoted. */
    private val NEEDS_QUOTING = charArrayOf(';', '"', '\n', '\r')

    private const val YELLOW = "ŽK"
    private const val RED = "ČK"
}
