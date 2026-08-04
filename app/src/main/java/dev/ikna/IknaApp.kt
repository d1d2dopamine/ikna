package dev.ikna

import android.app.Application
import dev.ikna.work.WorkScheduler

class IknaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WorkScheduler.schedule(this)
    }
}
