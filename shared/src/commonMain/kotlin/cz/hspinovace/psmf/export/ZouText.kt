package cz.hspinovace.psmf.export

/**
 * The report as a person reads it: the paper form's blocks, in the paper
 * form's order, in plain text.
 *
 * This is what goes in the body of the email to PSMF. It is the version
 * somebody can check against the pitch-side notes without opening
 * anything, which is why it keeps the form's own headings verbatim rather
 * than tidier ones.
 */
object ZouText {
    private const val RULE_WIDTH = 60
    private val RULE = "-".repeat(RULE_WIDTH)

    fun format(report: ZouReport): String =
        buildString {
            appendLine(ZouWords.TITLE)
            appendLine(RULE)
            appendHeader(report.header)
            report.lineups.forEach { appendLineup(it) }
            appendGoals(report)
            appendCards(report.cards)
            appendResult(report.result)
            appendAssessment(report.assessment)
            appendConfirmations(report)
        }.trimEnd() + "\n"

    private fun StringBuilder.appendHeader(header: ZouHeader) {
        appendLine("${header.homeTeam} - ${header.awayTeam}")
        appendLine()
        field(ZouLabels.Header.PITCH, header.pitch)
        field(ZouLabels.Header.DATE, header.dateWritten)
        field(ZouLabels.Header.TIME, header.timeWritten)
        field(ZouLabels.Header.LEAGUE, header.league)
        field(ZouLabels.Header.REFEREE, header.refereeWritten.orNotGiven())
        field(ZouLabels.Header.ASSISTANT, header.assistantWritten.orNotGiven())
        // The team that gets fined for a bad report, and not either of the
        // two above it.
        field(ZouLabels.Header.DELEGATING_TEAMS, header.delegatingTeam.orNotGiven())
        appendLine()
    }

    private fun StringBuilder.appendLineup(lineup: ZouLineup) {
        appendLine(RULE)
        appendLine("${lineup.side.mark}  ${lineup.teamName}")
        field(ZouLabels.Lineup.KIT_COLOUR, lineup.kitLabel)
        appendLine()
        appendLine(
            row(
                ZouLabels.Lineup.JERSEY_NUMBER,
                ZouLabels.Lineup.IDENTIFIER,
                ZouLabels.Lineup.PLAYER_NAME,
            ),
        )
        lineup.rows.forEach {
            appendLine(row(it.jerseyNumber?.toString().orEmpty(), it.identification, it.name))
        }
        appendLine()
    }

    private fun StringBuilder.appendGoals(report: ZouReport) {
        appendLine(RULE)
        appendLine("${ZouLabels.Goals.SECTION}:")
        if (report.goals.isEmpty()) {
            appendLine(ZouWords.NOT_GIVEN)
        } else {
            report.goals.forEach { goal ->
                appendLine(
                    listOf(
                        goal.side.mark,
                        goal.minute,
                        goal.jerseyNumber?.toString().orEmpty(),
                        // A goal may have no scorer; the form leaves it blank.
                        goal.scorer ?: ZouWords.NOT_GIVEN,
                        goal.scoreAfter,
                    ).joinToString("  "),
                )
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendCards(cards: ZouCards) {
        appendLine(RULE)
        appendLine("${ZouLabels.Cards.SECTION}:")
        when {
            // Not filled in at all. Different from "none were issued", and
            // the difference is what the struck-through boxes mean.
            !cards.accountedFor -> {
                appendLine(ZouWords.NOT_GIVEN)
            }

            cards.noneIssued -> {
                appendLine(ZouLabels.Cards.NONE_ISSUED)
            }

            else -> {
                appendCardBlock(ZouLabels.Cards.YELLOW, cards.yellow)
                appendCardBlock(ZouLabels.Cards.RED, cards.red)
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendCardBlock(
        title: String,
        cards: List<ZouCard>,
    ) {
        appendLine("$title:")
        if (cards.isEmpty()) {
            appendLine("  ${ZouLabels.Cards.NONE_ISSUED}")
            return
        }
        cards.forEach { card ->
            appendLine(
                "  " +
                    listOfNotNull(
                        card.side.mark,
                        card.minute,
                        card.jerseyNumber?.toString(),
                        card.name.takeIf { it.isNotBlank() },
                    ).joinToString(" ") + " - ${card.reason}",
            )
        }
    }

    private fun StringBuilder.appendResult(result: ZouResult?) {
        appendLine(RULE)
        if (result == null) {
            field(ZouLabels.Result.FINAL, ZouWords.NOT_GIVEN)
            appendLine()
            return
        }
        field(ZouLabels.Result.HALF_TIME, result.halfTime)
        field(ZouLabels.Result.FINAL, result.fullTime)
        field(ZouLabels.Result.WINNER, result.winner)
        appendLine()
    }

    private fun StringBuilder.appendAssessment(assessment: ZouAssessment) {
        appendLine(RULE)
        appendLine("${ZouLabels.Assessment.COMMENTARY} a hodnocení:")
        appendTeamAssessment(ZouLabels.Assessment.HOME, assessment.home)
        appendTeamAssessment(ZouLabels.Assessment.AWAY, assessment.away)
        appendLine()
        appendLine("${ZouLabels.Assessment.COMMENTARY}:")
        appendLine(assessment.commentary.ifBlank { ZouWords.NOT_GIVEN })
        appendLine()
    }

    private fun StringBuilder.appendTeamAssessment(
        mark: String,
        team: ZouTeamAssessment,
    ) {
        appendLine(
            listOf(
                mark,
                "${ZouLabels.Assessment.BEST_PLAYER}: ${team.bestPlayer?.toString() ?: ZouWords.NOT_GIVEN}",
                "${ZouLabels.Assessment.WAITING_TIME}: ${team.waitingTimeMinutes} ${ZouWords.MINUTES}",
                // Č and B carry fines, so an unassessed one says so rather
                // than reading as a pass.
                "${ZouLabels.Assessment.SHIRTS_NUMBERED}: ${ZouWords.of(team.shirtsProperlyNumbered)}",
                "${ZouLabels.Assessment.UNIFORM_KIT}: ${ZouWords.of(team.uniformKitColour)}",
            ).joinToString("  "),
        )
    }

    private fun StringBuilder.appendConfirmations(report: ZouReport) {
        appendLine(RULE)
        appendLine(ZouLabels.Lineup.CAPTAIN_CONFIRMS)
        appendLine()
        listOf(ZouWords.HOME_CAPTAIN, ZouWords.AWAY_CAPTAIN, ZouWords.REFEREE).forEach { party ->
            val confirmation = report.confirmations.firstOrNull { it.party == party }
            field(party, confirmation?.byWritten.orNotGiven())
        }
    }

    private fun StringBuilder.field(
        label: String,
        value: String,
    ) {
        appendLine("$label: $value")
    }

    private fun row(
        number: String,
        identification: String,
        name: String,
    ): String = number.padEnd(NUMBER_WIDTH) + identification.padEnd(IDENTIFIER_WIDTH) + name

    private const val NUMBER_WIDTH = 8
    private const val IDENTIFIER_WIDTH = 12
}

private fun String?.orNotGiven(): String = this?.takeIf { it.isNotBlank() } ?: ZouWords.NOT_GIVEN
