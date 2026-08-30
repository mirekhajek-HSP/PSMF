package cz.hspinovace.psmf.data.seed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * RULE: **a UUID in a seed file is permanent. Never regenerate one.**
 *
 * Match reports saved on a referee's phone store these ids, so:
 *
 * > **A regenerated UUID orphans every persisted match that referenced it.**
 *
 * When real psmf.cz data replaces the placeholder set, the importer must
 * preserve existing ids by matching on the natural key — the `ref` — and
 * mint new ones only for genuinely new entities. Regenerating from scratch
 * is what a scraper does by default, which is why this is written down as
 * code and not only as prose in the seed README.
 */
class SeedIdentityTest {
    private val existing =
        mapOf(
            "kominici" to "d58671d2-21b0-4d25-8728-8b280323f020",
            "sp-sumys" to "1f0e9a44-7c2b-4a71-9d3e-2b6c5f81aa10",
        )

    private var minted = 0

    private fun mint(ref: String): String = "minted-${++minted}-for-$ref"

    @Test
    fun anUnchangedRefKeepsItsId() {
        val assigned = SeedIdentity.assign(existing, listOf("kominici", "sp-sumys"), ::mint)

        assertEquals(existing, assigned)
        assertEquals(0, minted, "Nothing should have been minted")
    }

    @Test
    fun renamingATeamDoesNotChangeItsId() {
        // THE SCENARIO THIS EXISTS FOR. The display name changed; the ref
        // did not, because a ref is allowed to go stale and that is the
        // point of having one separate from the name.
        val assigned = SeedIdentity.assign(existing, listOf("kominici"), ::mint)

        assertEquals("d58671d2-21b0-4d25-8728-8b280323f020", assigned["kominici"])
        assertEquals(0, minted)
    }

    @Test
    fun aTransferredPlayerKeepsTheirIdBecauseRefsAreNotTeamScoped() {
        // Player refs are `ruzicka-radek`, never `kominici-01`. A
        // team-scoped ref would change on transfer -- which the analysis
        // permits once per season -- mint a new id, and orphan every match
        // the player already appears in.
        val players = mapOf("ruzicka-radek" to "d5f9e2e1-8a67-444f-8863-b3d1e8912e70")

        val afterTransfer = SeedIdentity.assign(players, listOf("ruzicka-radek"), ::mint)

        assertEquals(players, afterTransfer)
        assertEquals(0, minted)
    }

    @Test
    fun onlyGenuinelyNewRefsAreMinted() {
        val assigned = SeedIdentity.assign(existing, listOf("kominici", "sp-sumys", "novy-tym"), ::mint)

        assertEquals(existing["kominici"], assigned["kominici"])
        assertEquals(existing["sp-sumys"], assigned["sp-sumys"])
        assertEquals("minted-1-for-novy-tym", assigned["novy-tym"])
        assertEquals(1, minted, "Exactly one entity was new")
    }

    @Test
    fun regeneratingFromScratchIsWhatThisPrevents() {
        // The failure mode, written out: an importer that ignores what is
        // already on disk produces entirely different ids for the same
        // teams, and every saved report points at nothing.
        val naive = existing.keys.associateWith { mint(it) }
        val correct = SeedIdentity.assign(existing, existing.keys.toList(), ::mint)

        assertTrue(naive.keys == correct.keys)
        existing.keys.forEach { ref ->
            assertNotEquals(naive[ref], correct[ref])
            assertEquals(existing[ref], correct[ref])
        }
    }

    @Test
    fun aChangedRefReadsAsANewEntityWhichIsWhyRefsAreNotEdited() {
        // Fixing a "typo" in a ref is the one hand-edit that breaks
        // identity. The seed README says so; this says why.
        val assigned = SeedIdentity.assign(existing, listOf("kominici-fixed"), ::mint)

        assertNotEquals(existing["kominici"], assigned["kominici-fixed"])
        assertEquals(1, minted)
    }

    @Test
    fun refsThatDisappearAreReportedSoTheirIdsAreNotReused() {
        // A team that withdraws still has matches recorded against it.
        val departed = SeedIdentity.departedRefs(existing, listOf("kominici"))
        assertEquals(setOf("sp-sumys"), departed)
    }

    @Test
    fun nothingDepartsWhenEverythingIsStillThere() {
        assertEquals(emptySet(), SeedIdentity.departedRefs(existing, listOf("kominici", "sp-sumys", "new")))
    }
}
