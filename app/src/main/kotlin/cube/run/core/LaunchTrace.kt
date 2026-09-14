package cube.run.core

import android.os.SystemClock
import android.util.Log
import cube.run.BuildConfig

/** Debug-only timestamps for cold-launch work; never delays or gates rendering. */
object LaunchTrace {
    private val start = SystemClock.elapsedRealtime()
    fun mark(stage: String) {
        if (BuildConfig.DEBUG) Log.d("CUBE_START", "${SystemClock.elapsedRealtime()-start}ms $stage")
    }
}
