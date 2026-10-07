package cz.hspinovace.psmf.tools.leagueimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class NamesTest {
    @Test
    fun theTwoOrdersMatchAndNothingLooserDoes() {
        assertEquals(Names.matchKey("Bělohlávek Jan"), Names.matchKey("Jan Bělohlávek"))
        // A diacritic is a different name until a person says otherwise.
        assertNotEquals(Names.matchKey("Jamrik Pavel"), Names.matchKey("Pavel Jamrík"))
    }

    @Test
    fun theSurnameIsWhateverTurnsOneOrderIntoTheOther() {
        assertEquals("Bělohlávek" to "Jan", Names.split("Bělohlávek Jan", "Jan Bělohlávek"))
        assertEquals("Di Maria" to "Angel", Names.split("Di Maria Angel", "Angel Di Maria"))
        assertEquals("Novák" to "Jan Petr", Names.split("Novák Jan Petr", "Jan Petr Novák"))
        // With no lineup spelling to go by, the first word.
        assertEquals("Novák" to "Jan Petr", Names.split("Novák Jan Petr", null))
        assertNull(Names.split("Kurtemenov", null))
    }

    @Test
    fun aSlugIsAsciiAndReadable() {
        assertEquals("belohlavek-jan", Names.slug("Bělohlávek Jan"))
        assertEquals("rehacek-jan", Names.slug("Řeháček Jan"))
    }

    @Test
    fun aKitLabelIsKeptAndItsColoursAreABestEffort() {
        assertEquals(listOf("černá", "bílá"), KitLabels.split("černá, bílá"))
        assertEquals(listOf("pistáciová", "černá"), KitLabels.colours("pistáciovo-černá"))
        assertEquals(listOf("červená", "černá"), KitLabels.colours("červeno-černá"))
        assertEquals(listOf("světle zelená"), KitLabels.colours("světle zelená"))
    }
}
