package cz.hspinovace.psmf.usecase

import cz.hspinovace.psmf.domain.JerseyNumber
import cz.hspinovace.psmf.domain.KitId
import cz.hspinovace.psmf.domain.LineupConfirmation
import cz.hspinovace.psmf.domain.PersonName
import cz.hspinovace.psmf.domain.PlayerId
import kotlin.time.Instant

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

/**
 * A block a referee is in the middle of, over a squad of three or more, and
 * **not whole**: the first two are marked, the third is not.
 *
 *  - the first has a whole card number, a shirt number of their own, and is
 *    the captain, who has tapped to confirm;
 *  - the second is without a card, has a whole date of birth, and is the
 *    goalkeeper;
 *  - the third, not marked, has half a card number typed in the row.
 *
 * One of each kind of thing a lineup in progress holds -- a written value, a
 * date, a role, a tap, a kit, text with nothing written from it -- so a test
 * of what survives has something of every kind to lose.
 */
fun TeamLineupEntry.halfDone(wearing: KitId = kitId): TeamLineupEntry {
    val (first, second, third) = members
    return withPresence(first.player.id, true)
        .withPresence(second.player.id, true)
        .withMember(first.player.id) { it.withCardNumberTyped("59001").copy(jerseyNumber = JerseyNumber(10)) }
        .withMember(second.player.id) { it.copy(registrationCardPresent = false).withDateOfBirthTyped("18.5.1992") }
        .withMember(third.player.id) { it.withCardNumberTyped("59") }
        .withCaptain(first.player.id)
        .copy(
            goalkeeper = second.player.id,
            kitId = wearing,
            confirmation = LineupConfirmation(Instant.parse("2026-08-31T18:55:00Z"), PersonName.of("Novák Jan")),
        )
}
