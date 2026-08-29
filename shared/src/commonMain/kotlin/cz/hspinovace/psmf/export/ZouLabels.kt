package cz.hspinovace.psmf.export

/**
 * Field labels for the generated *Zápis o utkání*.
 *
 * **These are deliberately NOT localised resources and must never become
 * any.** The app UI is available in Czech, English and Ukrainian; the
 * report that goes to PSMF is always Czech, whatever language the referee
 * is reading the app in. Routing these through `composeResources` would
 * make the export language follow the UI language, which is wrong, and it
 * would do so silently.
 *
 * They live in `shared` rather than in `composeApp` for the same reason:
 * nothing here should be reachable from the localisation machinery.
 *
 * Field names are transcribed from the official form as catalogued in
 * `docs/LEAGUE_APP_ANALYSIS.md` section 2.5.
 */
object ZouLabels {

    /** Page 1 header, filled by the referee. Analysis section 2.5. */
    object Header {
        const val PITCH = "Hřiště"
        const val DATE = "Datum"
        const val TIME = "Čas"
        const val LEAGUE = "Liga"
        const val REFEREE = "Rozhodčí"
        const val ASSISTANT = "Asistent"

        /** Marks a licensed referee hired by the delegating team. */
        const val LICENSED_HIRE_MARK = "R"

        /** The teams that delegated the referees, not the teams playing. */
        const val DELEGATING_TEAMS = "Týmy"
    }

    /** Page 1 lineup block, one per team. Analysis section 2.5. */
    object Lineup {
        const val TEAM_NAME = "Název týmu"
        const val KIT_COLOUR = "Barva dresů"
        const val JERSEY_NUMBER = "Dres č."

        /**
         * One column holding either a registration-card number or, for a
         * player without their card, a date of birth. The model keeps this
         * as one field plus a discriminator for the same reason.
         */
        const val IDENTIFIER = "Číslo RP"
        const val PLAYER_NAME = "Příjmení a jméno"
        const val CAPTAIN_CONFIRMS =
            "Kapitáni potvrzují, že všichni hráči startují oprávněně"
    }

    /** Page 2 goals. Analysis section 2.5. */
    object Goals {
        const val SECTION = "Góly"
        const val TIME = "Čas"
        const val NUMBER = "Číslo"
        const val SCORER = "Střelec"
        const val SCORE_AFTER = "Stav"
    }

    /** Page 2 personal punishments. Analysis section 2.5. */
    object Cards {
        const val SECTION = "Osobní tresty"
        const val YELLOW = "Žluté karty (čas, číslo, jméno a důvod)"
        const val RED = "Červené karty (čas, číslo, jméno a důvod)"

        /** Written verbatim on the form for a second-yellow dismissal. */
        const val SECOND_YELLOW = "2. ŽK"

        /**
         * The paper form requires the boxes to be struck through when no
         * card was issued, so "none" is an affirmation rather than a blank.
         */
        const val NONE_ISSUED = "Bez karet"
    }

    /** Page 2 result. Analysis section 2.5. */
    object Result {
        const val HALF_TIME = "poločas"
        const val FINAL = "Konečný výsledek"
        const val WINNER = "Vítěz utkání"
    }

    /**
     * Page 2 referee assessment and mandatory commentary.
     * Analysis section 2.5 gives the legend for the abbreviations.
     */
    object Assessment {
        const val HOME = "D"
        const val AWAY = "H"

        /** Best player, given by jersey number. */
        const val BEST_PLAYER = "NH"

        /** Waiting time in minutes if a team was not ready at kickoff. */
        const val WAITING_TIME = "Čd"

        /** Whether the shirts of a team are properly numbered. */
        const val SHIRTS_NUMBERED = "Č"

        /** Whether a team turned out in uniform kit colour. */
        const val UNIFORM_KIT = "B"

        const val COMMENTARY = "Komentář"
    }

    /** Page 2 signatures. Analysis section 2.5. */
    object Signatures {
        const val CAPTAIN = "Podpis kapitána"
        const val REFEREE = "Podpis rozhodčího"
    }
}
