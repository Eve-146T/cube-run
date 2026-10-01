package cube.run.ui

import cube.run.core.Stage
import cube.run.core.VoidBeats

/**
 * The void show holds on its line until a tap. Call while polling for the shop to come back:
 * once the show is holding, it taps the line away (a first tap finishes a line still typing).
 */
internal fun tapAwayVoidLine() {
    if (Stage.voidClock >= VoidBeats.HOLD) Stage.voidSkips.incrementAndGet()
}
