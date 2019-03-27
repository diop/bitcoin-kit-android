package io.horizontalsystems.bitcoinkit.demo

import android.app.Application
import com.squareup.leakcanary.LeakCanary
import io.horizontalsystems.bitcoinkit.BitcoinCore

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return
        }

        LeakCanary.install(this)

        BitcoinCore.init(this)

        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }

}
