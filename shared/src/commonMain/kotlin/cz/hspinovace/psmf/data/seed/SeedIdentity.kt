package cz.hspinovace.psmf.data.seed

/**
 * **The rule that keeps persisted match reports pointing at the right
 * things when real psmf.cz data replaces the placeholder seed data.**
 *
 * # Why this exists
 *
 * Every team, player and fixture in a seed file carries an opaque UUID
 * `id`. A match report saved on a referee's phone stores those UUIDs. So:
 *
 * > **A regenerated UUID orphans every persisted match that referenced it.**
 *
 * The importer that eventually replaces the placeholder data must therefore
 * *preserve* existing ids by matching on a **natural key**, and mint new
 * ids only for genuinely new entities. Regenerating the file from scratch —
 * the obvious thing to do, and the thing a scraper does by default — is the
 * failure mode this guards against.
 *
 * # The natural key
 *
 * The natural key is the **`ref`**, the readable slug beside each id:
 *
 * - **Team** — `ref`. Survives a rename: the ref is allowed to go stale,
 *   and that is exactly the point of having one.
 * - **Player** — `ref`, and **not team-scoped**. The analysis permits one
 *   transfer per season; a team-scoped ref would change on transfer and
 *   orphan every match the player already appears in.
 * - **Fixture** — `ref`. Survives PSMF rescheduling a fixture, which they
 *   do before the final round.
 *
 * A ref that changes is therefore a *new entity* as far as this rule is
 * concerned. Editing a ref by hand is the one edit that breaks identity,
 * which is why the seed README says not to.
 */
object SeedIdentity {
    /**
     * Ids for [incomingRefs], keeping whatever id each ref already had.
     *
     * [mintId] is called **only** for refs that are not already known, and
     * is a parameter rather than a UUID call so that this is testable
     * without a random source.
     *
     * ```
     * val ids = SeedIdentity.assign(
     *     existingIdsByRef = currentTeams.associate { it.ref to it.id.value },
     *     incomingRefs = scraped.map { it.ref },
     *     mintId = { Uuid.random().toString() },
     * )
     * ```
     */
    fun assign(
        existingIdsByRef: Map<String, String>,
        incomingRefs: List<String>,
        mintId: (ref: String) -> String,
    ): Map<String, String> = incomingRefs.associateWith { ref -> existingIdsByRef[ref] ?: mintId(ref) }

    /**
     * Refs that were present before and are absent now.
     *
     * Their ids must **not** be reused for anything else. A team that
     * withdraws mid-season still has matches recorded against it, and
     * analysis section 6 lists withdrawal and removal as things that
     * genuinely happen.
     */
    fun departedRefs(
        existingIdsByRef: Map<String, String>,
        incomingRefs: List<String>,
    ): Set<String> = existingIdsByRef.keys - incomingRefs.toSet()
}
