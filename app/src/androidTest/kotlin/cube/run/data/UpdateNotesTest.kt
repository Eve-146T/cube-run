package cube.run.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** "In this update" shows once per set of notes, never on a fresh install, and every line parses. */
class UpdateNotesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("update_notes", Context.MODE_PRIVATE)

    @Test fun everyShippedLineParsesInEveryLanguage() {
        for (language in listOf("en", "de", "iw")) {
            val config = android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(language)) }
            val localized = context.createConfigurationContext(config)
            val lines = localized.resources.getStringArray(cube.run.R.array.update_notes)
            assertEquals("$language: every line is icon | title | text", lines.size, UpdateNotes.parse(lines).size)
            assertEquals("$language: the usual is present even without new features", UpdateNotes.USUAL, UpdateNotes.items(localized).last().icon)
        }
        assertEquals(listOf(UpdateNotes.Item("news", "A", "B | still text")), UpdateNotes.parse(arrayOf(" news | A | B | still text ", "broken", "x | | y")))
    }

    /** A version bump without new notes would show last update's news as this one's. */
    @Test fun theNotesAreWrittenForThisVersion() {
        assertEquals("Rewrite update_notes.xml for this version (docs/release-notes.md), then set update_notes_for",
            cube.run.BuildConfig.VERSION_NAME, context.getString(cube.run.R.string.update_notes_for))
        assertFalse(UpdateNotes.stale(context))
    }

    @Test fun theUsualAlwaysClosesTheList() {
        val items = UpdateNotes.items(context)
        assertEquals(UpdateNotes.USUAL, items.last().icon)
        assertEquals(UpdateNotes.news(context), items.dropLast(1))
        assertTrue("The usual is never listed as news", UpdateNotes.news(context).none { it.icon == UpdateNotes.USUAL })
    }

    @Test fun showsOncePerNotesAndNotesChangingShowsAgain() {
        val saved = prefs.all
        try {
            prefs.edit().putString("seen", "an older update").commit()
            assertEquals("Only feature news warrants a pop-up", UpdateNotes.news(context).isNotEmpty(), UpdateNotes.takeUnseen(context))
            assertFalse("Only once", UpdateNotes.takeUnseen(context))
            assertEquals(UpdateNotes.key(context), prefs.getString("seen", null))
        } finally {
            prefs.edit().clear().apply { saved.forEach { (k, v) -> if (v is String) putString(k, v) } }.commit()
        }
    }

    @Test fun aSaveWithoutRunsGetsNoNotes() {
        val saved = prefs.all
        val progress = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val savedRuns = progress.getInt("runs", 0)
        try {
            prefs.edit().clear().commit()
            progress.edit().putInt("runs", 0).commit(); Progress.init(context)
            assertFalse("Nothing to compare with", UpdateNotes.takeUnseen(context))
            assertFalse(UpdateNotes.takeUnseen(context))
        } finally {
            progress.edit().putInt("runs", savedRuns).commit(); Progress.init(context)
            prefs.edit().clear().apply { saved.forEach { (k, v) -> if (v is String) putString(k, v) } }.commit()
        }
    }
}
