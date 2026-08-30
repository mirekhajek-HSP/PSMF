package cz.hspinovace.psmf.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Shared test data, built to resemble the worked example in
 * `docs/LEAGUE_APP_ANALYSIS.md` section 2.5 so that assertions can be
 * checked against a real report rather than against invented numbers.
 *
 * Hand-written, not generated or mocked: shared tests use `kotlin.test`
 * and plain fakes, because MockK is JVM-only and cannot compile for iOS.
 *
 * Ids here are short readable strings rather than the UUIDs the seed files
 * carry. That is fine and deliberate — the domain treats an id as opaque,
 * and a test that says `TeamId("kominici")` is far easier to read than one
 * that says `TeamId("d58671d2-...")`. The UUID rule is tested where it
 * matters, against the shipped data and in `SeedIdentityTest`.
 */
object Fixtures {
    val seasonId = SeasonId("2026-podzim")
    val groupId = GroupId("6k")
    val homeTeamId = TeamId("kominici")
    val awayTeamId = TeamId("sp-sumys")
    val fixtureId = FixtureId("6k-r1-01")
    val matchId = MatchId("match-1")

    val homePrimaryKit = Kit(KitId("kit-kominici-1"), "modrá", listOf("modrá"))
    val homeAlternateKit = Kit(KitId("kit-kominici-2"), "bílo-modrá", listOf("bílá", "modrá"))
    val awayPrimaryKit = Kit(KitId("kit-sumys-1"), "černo-bílá", listOf("černá", "bílá"))

    val homeTeam =
        Team(
            id = homeTeamId,
            ref = "kominici",
            groupId = groupId,
            name = "Kominíci",
            kits = listOf(homePrimaryKit, homeAlternateKit),
        )

    val awayTeam =
        Team(
            id = awayTeamId,
            ref = "sp-sumys",
            groupId = groupId,
            name = "Sp. Sumýš",
            kits = listOf(awayPrimaryKit),
        )

    val group =
        Group(
            id = groupId,
            seasonId = seasonId,
            name = "6. liga K",
            reportCode = "6K",
        )

    val fixture =
        Fixture(
            id = fixtureId,
            ref = "6k-r1-01",
            groupId = groupId,
            round = 1,
            date = LocalDate(2026, 8, 31),
            time = LocalTime(19, 0),
            venue = VenueCode("ZAKOS"),
            homeTeamId = homeTeamId,
            awayTeamId = awayTeamId,
        )

    /** The date of birth from the worked example: `990121` is 21 Jan 1999. */
    val hlokDateOfBirth = LocalDate(1999, 1, 21)

    fun player(
        ref: String,
        surname: String,
        first: String,
        number: Int?,
        dateOfBirth: LocalDate? = LocalDate(1990, 6, 15),
        rpNumber: RpNumber? = null,
        discipline: DisciplinaryRecord? = null,
    ) = Player(
        id = PlayerId(ref),
        ref = ref,
        teamId = homeTeamId,
        name = PlayerName(PersonName.of(surname), PersonName.of(first)),
        rpNumber = rpNumber,
        dateOfBirth = dateOfBirth,
        birthNumber = null,
        defaultJerseyNumber = JerseyNumber.orNull(number),
        discipline = discipline,
    )

    /** Defaults to a date of birth, which is what the seed data actually has. */
    fun identification(
        value: String = "900615",
        source: IdentificationSource = IdentificationSource.DATE_OF_BIRTH,
    ) = ReportedIdentification(value, source)

    fun appearance(
        id: String,
        playerId: String,
        number: Int?,
        reportedIdentification: ReportedIdentification = identification(),
    ) = Appearance(
        id = AppearanceId(id),
        playerId = PlayerId(playerId),
        jerseyNumber = JerseyNumber.orNull(number),
        reportedIdentification = reportedIdentification,
    )

    fun lineup(
        side: TeamSide,
        vararg appearances: Appearance,
        kit: Kit? = null,
    ) = Lineup.wearing(
        side = side,
        teamId = if (side == TeamSide.HOME) homeTeamId else awayTeamId,
        appearances = appearances.toList(),
        // Snapshots the label as well as the reference; see Lineup.kitLabel.
        kit = kit ?: if (side == TeamSide.HOME) homePrimaryKit else awayPrimaryKit,
    )

    val officials =
        RefereeAssignment(
            main = Official(PersonName.of("Jiri Vlk")),
            assistant = Official(PersonName.of("Roman Liska"), licensedHire = true),
            delegatingTeam = "Kominici",
        )

    val bacaAppearance = appearance("app-baca", "p-baca", 13)
    val houzevAppearance = appearance("app-houzev", "p-houzev", 12)
    val poupeAppearance = appearance("app-poupe", "p-poupe", 9)

    val homeLineup = lineup(TeamSide.HOME, houzevAppearance, poupeAppearance)
    val awayLineup = lineup(TeamSide.AWAY, bacaAppearance)

    val confirmedAt = kotlin.time.Instant.parse("2026-08-31T20:05:00Z")
    val kickoffAt = kotlin.time.Instant.parse("2026-08-31T19:00:00Z")

    /** A match with header and lineups but nothing recorded yet. */
    fun matchInSetup() =
        Match(
            id = matchId,
            fixtureId = fixtureId,
            groupId = groupId,
            officials = officials,
            homeLineup = homeLineup,
            awayLineup = awayLineup,
        )

    fun confirmation(
        party: ConfirmingParty,
        asDeputy: Boolean = false,
    ) = Confirmation(
        party = party,
        at = confirmedAt,
        confirmedBy = PersonName.of(if (asDeputy) "Lepis" else "Novak"),
        asDeputy = asDeputy,
    )
}
