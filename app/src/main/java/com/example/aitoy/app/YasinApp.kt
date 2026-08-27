package com.example.aitoy.app

import android.app.Application
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.Rive

class YasinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Rive.init(this, RendererType.Rive)
    }

    val appContainer: AppContainer by lazy {
        AppContainer(appContext = this)
    }
}
