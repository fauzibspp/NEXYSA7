package com.nexysa7.crypto

import android.app.Application
import com.nexysa7.crypto.di.AppContainer

class NexysA7Application : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
