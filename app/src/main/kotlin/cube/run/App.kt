package cube.run

import android.app.Application
import cube.run.core.Haptics
import cube.run.data.Progress
import cube.run.data.Scores
import cube.run.data.Settings
import cube.run.core.SoundFx

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Scores.init(this)
        Settings.init(this)
        Progress.init(this)
        Haptics.init(this)
        SoundFx.init(this)
    }
}
