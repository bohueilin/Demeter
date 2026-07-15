package com.demeter.app

import android.app.Application
import com.demeter.app.data.DemeterDb
import com.demeter.app.data.DemeterRepository
import com.demeter.app.platform.AlarmScheduler
import com.demeter.app.platform.NotificationHelper
import com.demeter.app.platform.Reconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    val db: DemeterDb = DemeterDb.build(app)
    val repository: DemeterRepository = DemeterRepository(db)
    val alarmScheduler: AlarmScheduler = AlarmScheduler(app)
    val reconciler: Reconciler = Reconciler(repository, alarmScheduler)
}

class DemeterApp : Application() {

    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)
        // Launch-time repair: platform alarms are derived state and never trusted.
        appScope.launch { container.reconciler.reconcile("app_launch") }
    }
}
