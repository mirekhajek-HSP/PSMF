package cz.hspinovace.psmf.data.league

import cz.hspinovace.psmf.data.seed.LeagueGroup
import cz.hspinovace.psmf.data.team.JerseyOverrideRepository
import cz.hspinovace.psmf.domain.FixtureId
import cz.hspinovace.psmf.domain.GroupId
import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.PlayerId

/**
 * League data with the referee's corrected jersey numbers laid over it.
 *
 * # Why a decorator
 *
 * Two things want the corrected number and neither should have to know
 * about the other: the Týmy tab, which is where it is edited, and the
 * lineup screen, which pre-fills from it. Putting the lookup inside
 * [SeedLeagueRepository] would mix immutable shipped data with mutable
 * device state in one class; putting it in each caller would mean the next
 * caller forgets.
 *
 * Wrapping instead keeps the layering honest and puts the seam in exactly
 * one place — the Koin module, which is the only file that knows both
 * halves exist.
 *
 * # What it does not touch
 *
 * Only [cz.hspinovace.psmf.domain.Player.defaultJerseyNumber], which is a
 * pre-fill. Everything a report says about a number lives on the
 * appearance, and appearances do not come through here.
 *
 * # Cost
 *
 * The overrides are read on every call rather than cached, because the
 * whole point is that an edit takes effect. The delegate caches the
 * expensive half — parsing the seed files — and this reads a table that
 * holds one row per number a referee has ever corrected, so in practice
 * zero.
 */
class JerseyOverridingLeagueRepository(
    private val delegate: LeagueRepository,
    private val overrides: JerseyOverrideRepository,
) : LeagueRepository {
    override suspend fun groups(): List<LeagueGroup> {
        val corrected = overrides.overrides()
        return delegate.groups().map { it.withJerseyNumbers(corrected) }
    }

    override suspend fun group(id: GroupId): LeagueGroup? = delegate.group(id)?.withJerseyNumbers(overrides.overrides())

    override suspend fun fixture(id: FixtureId): LoadedFixture? =
        delegate.fixture(id)?.let { loaded ->
            loaded.copy(leagueGroup = loaded.leagueGroup.withJerseyNumbers(overrides.overrides()))
        }
}

/**
 * Returns the group with [corrected] applied to its players.
 *
 * Returns the receiver untouched when there is nothing to apply, which is
 * the normal case and avoids rebuilding a 144-player list to change
 * nothing.
 */
internal fun LeagueGroup.withJerseyNumbers(corrected: Map<PlayerId, JerseyNumber>): LeagueGroup =
    if (corrected.isEmpty()) {
        this
    } else {
        copy(
            players =
                players.map { player ->
                    val number = corrected[player.id]
                    if (number == null || number == player.defaultJerseyNumber) {
                        player
                    } else {
                        player.copy(defaultJerseyNumber = number)
                    }
                },
        )
    }
