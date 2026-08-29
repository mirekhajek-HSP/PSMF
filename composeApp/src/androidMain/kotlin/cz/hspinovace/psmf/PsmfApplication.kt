package cz.hspinovace.psmf

import android.app.Application
import cz.hspinovace.psmf.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class PsmfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger()
            androidContext(this@PsmfApplication)
        }
    }
}
