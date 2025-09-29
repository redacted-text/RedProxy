package net.redproxy

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.UnknownHostException

val SUPPORTED_TYPES = arrayOf("socks5")

const val CONFIG_LOGIN_PASS = """base {
	log_debug = off;
	log_info = off;
	log = stderr;
	daemon = on;
	redirector = iptables;
}

redsocks {
	bind = "127.0.0.1:10000";
	relay = "IP:PORT";
	type = TYPE;
	login = "LOGIN";
	password = "PASSWORD";
}

tcpdns {
	bind = "127.0.0.1:10004";
	tcpdns1 = "8.8.8.8";
	tcpdns2 = "8.8.4.4";
	timeout = 10;
}"""

const val CONFIG = """base {
	log_debug = off;
	log_info = off;
	log = stderr;
	daemon = on;
	redirector = iptables;
}

redsocks {
	bind = "127.0.0.1:10000";
	relay = "IP:PORT";
	type = TYPE;
}

tcpdns {
	bind = "127.0.0.1:10004";
	tcpdns1 = "8.8.8.8";
	tcpdns2 = "8.8.4.4";
	timeout = 10;
}"""


fun prepareRedsocks(context: Context) {
    if (!File("/data/local/tmp/rs/redsocks2").exists()) {
        val inputStream: InputStream = context.assets.open("redsocks2")
        val destinationFile = File(context.filesDir, "redsocks2")
        val outputStream = FileOutputStream(destinationFile)

        val buffer = ByteArray(1024)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            outputStream.write(buffer, 0, read)
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()
        SuUtil.exec2("mkdir /data/local/tmp/rs;mv ${context.filesDir}/redsocks2 mkdir /data/local/tmp/rs/;chmod 777 /data/local/tmp/rs/redsocks2")
    }
    disableRedsocks()
}

fun enableRedsocks() {
    SuUtil.exec2("iptables -D OUTPUT -p udp -j REDSOCKS_F -w 2>/dev/null;" +
            "iptables -F REDSOCKS_F -w 2>/dev/null;" +
            "iptables -X REDSOCKS_F -w 2>/dev/null;" +
            "iptables -t nat -F OUTPUT -w 2>/dev/null;" +
            "iptables -t nat -F REDSOCKS -w 2>/dev/null;" +
            "iptables -t nat -X REDSOCKS -w 2>/dev/null;" +
            "iptables -t nat -N REDSOCKS -w;" +
            "iptables -t nat -A OUTPUT -p tcp -j REDSOCKS -w;" +
            "iptables -t nat -A OUTPUT -p udp -j REDSOCKS -w;" +
            "iptables -N REDSOCKS_F -w;" +
            "iptables -A OUTPUT -p udp -j REDSOCKS_F -w;" +
            "iptables -t nat -A REDSOCKS -p udp --dport 53 -j REDIRECT --to-ports 10004 -w;" +
            "iptables -t nat -A REDSOCKS -d 0.0.0.0/8 -j RETURN -w;" +
            "iptables -t nat -A REDSOCKS -d 10.0.0.0/8 -j RETURN -w;" +
            "iptables -t nat -A REDSOCKS -d 127.0.0.0/8 -j RETURN -w;" +
            "iptables -t nat -A REDSOCKS -d 169.254.0.0/16 -j RETURN -w;" +
            "iptables -t nat -A REDSOCKS -d 172.16.0.0/12 -j RETURN -w;" +
            "iptables -t nat -A REDSOCKS -d 192.168.0.0/16 -j RETURN -w;" +
            "iptables -t nat -A REDSOCKS -d 224.0.0.0/4 -j RETURN -w;" +
            "iptables -t nat -A REDSOCKS -d 240.0.0.0/4 -j RETURN -w;" +
            "iptables -t nat -A REDSOCKS -d 43.159.20.117 -j RETURN -w;" +
            "iptables -A REDSOCKS_F -p udp --dport 19302:19305 -j DROP -w;" +
            "iptables -A REDSOCKS_F -p udp --dport 3478 -j DROP -w;" +
            "iptables -A REDSOCKS_F -p udp --dport 5349 -j DROP -w;" +
            "iptables -A REDSOCKS_F -p udp --dport 443 -j DROP -w;" +
            "iptables -t nat -A REDSOCKS -p tcp -j REDIRECT --to-ports 10000 -w")
    SuUtil.exec2("iptables -t nat -S OUTPUT;iptables -t nat -S REDSOCKS")
    SuUtil.exec2("cd /data/local/tmp/rs;/data/local/tmp/rs/redsocks2 -x -t -c /data/local/tmp/rs/redsocks2.conf")
    SuUtil.exec2("cd /data/local/tmp/rs;/data/local/tmp/rs/redsocks2 -x -c /data/local/tmp/rs/redsocks2.conf")
}

fun isRunning(): Boolean {
    return SuUtil.exec2("pgrep -x redsocks2")[0] != null
}

fun reloadRedsocks() {
    SuUtil.exec2("kill -9 \$(pgrep -x redsocks2)")
    SuUtil.exec2("cd /data/local/tmp/rs;/data/local/tmp/rs/redsocks2 -x -c /data/local/tmp/rs/redsocks2.conf")
}

fun disableRedsocks() {
    SuUtil.exec2("iptables -D OUTPUT -p udp -j REDSOCKS_F -w 2>/dev/null;" +
            "iptables -F REDSOCKS_F -w 2>/dev/null;" +
            "iptables -X REDSOCKS_F -w 2>/dev/null;" +
            "iptables -t nat -F OUTPUT -w 2>/dev/null;" +
            "iptables -t nat -F REDSOCKS -w 2>/dev/null;" +
            "iptables -t nat -X REDSOCKS -w 2>/dev/null")
    SuUtil.exec2("kill -9 \$(pgrep -x redsocks2)")
}

suspend fun setProxyRedsocks(proxy: String) { withContext(Dispatchers.IO) {
    val match = Regex("^([\\w+]+)://((.*):(.*)@)?([^@]+):(\\d+)\$").matchEntire(proxy) ?: throw RuntimeException("Invalid proxy")
    var type = match.groups[1]!!.value
    if (!SUPPORTED_TYPES.contains(type)) throw RuntimeException("Invalid proxy")
    val login = match.groups[3]?.value
    val password = match.groups[4]?.value
    val host = match.groups[5]!!.value
    val port = match.groups[6]!!.value

    val address = try {
        InetAddress.getAllByName(host)[0].hostAddress!!
    } catch (e: UnknownHostException) {
        throw RuntimeException("Invalid proxy")
    }

    Log.e(BuildConfig.APPLICATION_ID, "${login} ${password} $host $address $port")

    val config = if (login != null && password != null )
        CONFIG_LOGIN_PASS
            .replace("TYPE", type)
            .replace("LOGIN", login)
            .replace("PASSWORD", password)
            .replace("IP", address)
            .replace("PORT", port)
    else
        CONFIG
            .replace("TYPE", type)
            .replace("IP", address)
            .replace("PORT", port)
    SuUtil.exec2("rm /data/local/tmp/rs/redsocks2.conf;echo '${config}' > /data/local/tmp/rs/redsocks2.conf;chmod 777 /data/local/tmp/rs/redsocks2.conf")
} }