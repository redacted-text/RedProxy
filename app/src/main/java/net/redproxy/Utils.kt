package net.redproxy

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException


suspend fun <T> retry(block: suspend () -> T): T {
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(BuildConfig.APPLICATION_ID, "retry: ", e)
            delay(1000)
        } catch (e: AssertionError) {
            Log.e(BuildConfig.APPLICATION_ID, "retry: ", e)
            delay(1000)
        }
    }
}

public suspend fun SoaxRest.checkIp() = withContext(Dispatchers.IO) {
    val ip = retry { return@retry this@checkIp.info().execute().body()!! }
    return@withContext withContext(Dispatchers.Main) {
        return@withContext "${countryCodeToEmojiFlag(ip.data.country_code)} ${ip.data.country_code} — ${ip.data.ip}"
    }
}

fun countryCodeToEmojiFlag(countryCode: String): String {
    return try {
        countryCode
            .uppercase(Locale.US)
            .map { char ->
                Character.codePointAt("$char", 0) - 0x41 + 0x1F1E6
            }
            .map { codePoint ->
                Character.toChars(codePoint)
            }
            .joinToString(separator = "") { charArray ->
                String(charArray)
            }
    } catch (e: Exception) {
        ""
    }
}