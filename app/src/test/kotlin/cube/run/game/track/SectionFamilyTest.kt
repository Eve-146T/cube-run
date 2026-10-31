package cube.run.game.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionFamilyTest {
    private fun family(name: String) = SectionFamily.of(Sections.lib.first { it.name == name })

    @Test fun sectionsFileThemselvesByWhatTheyAreAbout() {
        assertEquals(SectionFamily.MOVES, family("FIRST STEPS"))
        assertEquals(SectionFamily.MOVES, family("THE WAVE"))
        assertEquals(SectionFamily.ROOFS, family("HIGH ROAD"))
        assertEquals(SectionFamily.ROOFS, family("BIG AIR"))     // pads win over the pincer they clear
        assertEquals(SectionFamily.MACHINES, family("SWING SET"))
        assertEquals(SectionFamily.CRUSHERS, family("STOMP"))
        assertEquals(SectionFamily.PITS, family("TAR & FEATHER")) // tar wins over the wall after it
        assertEquals(SectionFamily.MIXED, family("HELLRIDE"))
        assertEquals(SectionFamily.COINS, family("COIN CANYON"))
    }

    @Test fun everyFamilyHoldsSeveralSections() {
        val sizes = Sections.lib.groupingBy { SectionFamily.of(it) }.eachCount()
        for (f in SectionFamily.entries) assertTrue("$f has ${sizes[f]}: $sizes", (sizes[f] ?: 0) >= 3)
    }
}
