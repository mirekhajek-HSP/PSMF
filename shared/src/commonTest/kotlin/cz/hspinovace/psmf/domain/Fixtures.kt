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
 */
object Fixtures {

    val seasonId = SeasonId("2026-podzim")
    val groupId = GroupId("6k")
    val homeTeamId = TeamId("kominici")
    val awayTeamId = TeamId("sp-sumys")
    val fixtureId = FixtureId("6k-r1-01")
    val matchId = MatchId("match-1")

    val group = Group(
        id = groupId,
        seasonId = seasonId,
        name = "6. liga K",
        reportCode = "6K",
    )

    val fixture = Fixture(
        id = fixtureId,
        groupId = groupId,
        round = 1,
        date = LocalDate(2026, 8, 31),
        time = LocalTime(19, 0),
        venue = VenueCode("ZAKOS"),
        homeTeamId = homeTeamId,
        awayTeamId = awayTeamId,
    )

    fun player(id: String, surname: String, given: String, number: Int?) = Player(
        id = PlayerId(id),
        teamId = homeTeamId,
        name = PlayerName(PersonName.of(surname), PersonName.of(given)),
        identifier = null,
        defaultJerseyNumber = JerseyNumber.orNull(number),
    )

    fun appearance(id: String, playerId: String, number: Int?, identifier: PlayerIdentifier? = null) =
        Appearance(
            id = AppearanceId(id),
            playerId = PlayerId(playerId),
            jerseyNumber = JerseyNumber.orNull(number),
            identifier = identifier,
        )

    fun lineup(side: TeamSide, vararg appearances: Appearance) = Lineup(
        side = side,
        teamId = if (side == TeamSide.HOME) homeTeamId else awayTeamId,
        appearances = appearances.toList(),
        kitColour = if (side == TeamSide.HOME) "modrá" else "černo-bílá",
    )

    val officials = RefereeAssignment(
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

    /** A match with header and lineups but nothing recorded yet. */
    fun matchInSetup() = Match(
        id = matchId,
        fixtureId = fixtureId,
        groupId = groupId,
        officials = officials,
        homeLineup = homeLineup,
        awayLineup = awayLineup,
    )

    fun confirmation(party: ConfirmingParty, asDeputy: Boolean = false) = Confirmation(
        party = party,
        at = confirmedAt,
        confirmedBy = PersonName.of(if (asDeputy) "Lepis" else "Novak"),
        asDeputy = asDeputy,
    )
}
