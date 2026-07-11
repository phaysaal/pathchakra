package com.seenslide.teacher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.seenslide.teacher.core.worker.AutoBackupWorker
import com.seenslide.teacher.core.worker.SyncWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@HiltAndroidApp
class OStudiApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageLoaderEntryPoint {
        fun imageLoader(): ImageLoader
    }

    override fun newImageLoader(): ImageLoader =
        EntryPointAccessors.fromApplication(this, ImageLoaderEntryPoint::class.java)
            .imageLoader()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
        AutoBackupWorker.schedule(this)
    }
}
