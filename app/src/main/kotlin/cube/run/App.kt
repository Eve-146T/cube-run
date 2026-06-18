package cube.run

import android.app.Application
import cube.run.core.Haptics
import cube.run.core.Scores
import cube.run.core.Settings
import cube.run.core.SoundFx

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Scores.init(this)
        Settings.init(this)
        Haptics.init(this)
        SoundFx.init(this)
    }
}
