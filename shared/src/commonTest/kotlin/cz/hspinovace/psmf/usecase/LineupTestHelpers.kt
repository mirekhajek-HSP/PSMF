package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.PlayerId

/**
 * A block a referee has finished: everyone came, each card's number is
 * typed, a captain and a goalkeeper are marked. The lineup tests that are
 * about something other than marking use it, so that the rules of marking are
 * said in one place and nowhere else.
 *
 * The numbers are five digits, `59001` upwards, as the form's examples are.
 */
fun TeamLineupEntry.everyoneCame(
    captain: PlayerId? = members.first().player.id,
    goalkeeper: PlayerId? = members.last().player.id,
): TeamLineupEntry =
    members
        .foldIndexed(this) { index, team, member ->
            team
                .withPresence(member.player.id, true)
                .withMember(member.player.id) { it.withCardNumberTyped("5900${index + 1}") }
        }.withCaptain(captain)
        .copy(goalkeeper = goalkeeper)

fun LineupEntry.everyoneCame(): LineupEntry = LineupEntry(home.everyoneCame(), away.everyoneCame())
