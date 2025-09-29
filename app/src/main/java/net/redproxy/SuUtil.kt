package net.redproxy

import android.util.Log
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

public suspend fun waitForRoot() {
    while (SuUtil.exec2("echo su")[0] != "su") {
        delay(1000)
    }
}

object SuUtil {

    private const val TAG = "SU_UTIL"

    fun exec (cmd: String): ArrayList<String?> {
        return exec(arrayListOf(cmd))
    }

    // https://stackoverflow.com/a/11311955/466693
    fun exec (cmds: List<String>): ArrayList<String?> {
        // do su process
        val proc = Runtime.getRuntime().exec("su") ?: return arrayListOf(null, null)
        val os = DataOutputStream(proc.outputStream)
        for (cmd in cmds) {
            os.writeBytes(cmd + "\n")
        }
        os.writeBytes("exit\n")
        os.flush()
        os.close()
        proc.waitFor()

        // return resp
        return extractOutput(proc)
    }

    fun exec2 (cmd: String): ArrayList<String?> {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        proc.waitFor()
        return extractOutput(proc)
    }
    fun exec2mm (cmd: String): ArrayList<String?> {
        val proc = Runtime.getRuntime().exec(arrayOf("su", "--mount-master", "-c", cmd))
        proc.waitFor()
        return extractOutput(proc)
    }

    private fun extractOutput (proc: Process): ArrayList<String?> {
        // gather resp
        val stdInput = BufferedReader(InputStreamReader(proc.inputStream))
        val stdError = BufferedReader(InputStreamReader(proc.errorStream))
        var s: String? // null included
        var o: String? = ""
        var e: String? = ""
        while (stdInput.readLine().also { s = it } != null) { o += s }
        while (stdError.readLine().also { s = it } != null) { e += s }

        // if blank, cast to null
        if (o.isNullOrBlank()) o = null
        if (e.isNullOrBlank()) e = null

        // debug
        Log.d(TAG, "[success] $o")
        Log.d(TAG, "[error  ] $e")

        // return output, error
        return arrayListOf(o, e)
    }
}