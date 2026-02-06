package com.example.inventory

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.inventory.data.AppContainer

class InventoryApplication : Application() {
    lateinit var container: AppContainer
        private set

    companion object {
        lateinit var INSTANCE: InventoryApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        runCatching {
            System.loadLibrary("paddle_lite_jni")
        }
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            container.clearOnnxSessions()
        }
    }

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            container.clearOnnxSessions()
        }
    }
}
