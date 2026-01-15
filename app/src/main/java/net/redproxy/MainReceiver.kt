package net.redproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "net.redproxy.SET_PROXY") return
        val proxy = intent.getStringExtra("proxy")
        Log.e(BuildConfig.APPLICATION_ID, "Received ${proxy}")
        if (proxy == null) return
        GlobalScope.launch {
            if (!isRunning()) prepareRedsocks(context)
            if (proxy == "" || proxy == "null") { disableRedsocks(); return@launch }
            try {
                if (isRunning()) disableRedsocks()
                enableRedsocks(proxy)
            } catch (e: RuntimeException) {
                disableRedsocks()
            }
        }
    }
}