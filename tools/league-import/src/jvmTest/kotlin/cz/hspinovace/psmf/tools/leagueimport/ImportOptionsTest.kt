package cz.hspinovace.psmf.tools.leagueimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImportOptionsTest {
    @Test
    fun aRunWithNoCompetitionNamedReadsHanspaulskaLiga6AsItAlwaysDid() {
        assertEquals(listOf(Competition.HANSPAULSKA_6), ImportOptions.parse(emptyArray()).competitions)
    }

    @Test
    fun namedCompetitionsAreReadInTheOrderGiven() {
        val options = ImportOptions.parse(arrayOf("--competition", "hl6", "--competition", "uvl1", "--offline"))

        assertEquals(listOf(Competition.HANSPAULSKA_6, Competition.ULTRAVETERANSKA_1), options.competitions)
        assertEquals(true, options.offline)
    }

    @Test
    fun anUnknownCompetitionIsRefusedRatherThanGuessed() {
        assertFailsWith<IllegalStateException> { ImportOptions.parse(arrayOf("--competition", "uvl2")) }
    }
}
