package net.redproxy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import com.example.compose.AppTheme
import kotlin.collections.toSet
import kotlin.streams.asSequence

class MainActivity : ComponentActivity() {

    private val soax: SoaxRest = Retrofit.Builder()
        .baseUrl("https://checker.soax.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val originalRequest = chain.request();
                    val requestWithCloseHeader = originalRequest.newBuilder()
                        .header("Connection", "close")
                        .build();
                    chain.proceed(requestWithCloseHeader);
                }
                .connectTimeout(30L, TimeUnit.SECONDS)
                .readTimeout(30L, TimeUnit.SECONDS)
                .writeTimeout(30L, TimeUnit.SECONDS)
                .callTimeout(30L, TimeUnit.SECONDS)
                .build()
        )
        .build().create()

    private val status = mutableStateOf("")
    private val speed = mutableStateOf("")
    private val proxyString = mutableStateOf("")
    private val prepared = mutableStateOf(false)
    private val running = mutableStateOf(false)
    private val prefs by lazy { getSharedPreferences("main", MODE_PRIVATE) }
    private val list = mutableStateOf(emptyList<String>())

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val status by this@MainActivity.status
            val speed by this@MainActivity.speed
            var proxyString by remember { proxyString }
            val prepared by prepared
            val running by running
            val list by list
            AppTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.primary,
                            ),
                            title = {
                                Text(this.title.toString())
                            }
                        )
                    },
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize(1f)) {
                        Text(status, modifier = Modifier.fillMaxWidth().padding(all=12.dp), textAlign = TextAlign.Center)
                        Text(speed, modifier = Modifier.fillMaxWidth().padding(all=12.dp), textAlign = TextAlign.Center)
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical=24.dp)) {
                            Spacer(modifier = Modifier.weight(1f))
                            Button(enabled = prepared && isProxyStringValid(proxyString), onClick = { launch() }) {
                                Text(getString(if (running) R.string.restart else R.string.start).uppercase())
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Button(enabled = prepared, onClick = { lifecycleScope.launch {
                                checkIp()
                            } }) {
                                Text(getString(R.string.check_ip).uppercase())
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Button(enabled = prepared && running, onClick = { lifecycleScope.launch {
                                disableRedsocks()
                                checkIsRunning()
                                checkIp()
                            } }) {
                                Text(getString(R.string.stop).uppercase())
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth().padding(horizontal=24.dp),
                            value = proxyString,
                            onValueChange = { proxyString = it; prefs.edit {
                                putString(
                                    "proxyString",
                                    it
                                )
                            } },
                            singleLine = true
                        )
                        LazyColumn(modifier = Modifier.padding(top = 24.dp).fillMaxWidth(1f).defaultMinSize(minHeight = 200.dp)) {
                            items(items = list) { proxy ->
                                Row(modifier = Modifier.clickable {
                                    proxyString = proxy
                                    prefs.edit {
                                        putString(
                                            "proxyString",
                                            proxy
                                        )
                                    }
                                    launch()
                                }) {
                                    Text(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), text = proxy)
                                }
                            }
                            if (list.isNotEmpty()) item {
                                Row(modifier = Modifier.clickable {
                                    clear()
                                }) {
                                    Text(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center, text = getString(R.string.clear).uppercase())
                                }
                            }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            this@MainActivity.proxyString.value = prefs.getString("proxyString", null) ?: ""
            this@MainActivity.list.value = (prefs.getStringSet("list", null) ?: emptySet()).toList()
            this@MainActivity.status.value = getString(R.string.root)
            waitForRoot()
            if (!isRunning()) {
                this@MainActivity.status.value = getString(R.string.preparation)
                prepareRedsocks(this@MainActivity)
            }
            checkIsRunning()
            prepared.value = true
            checkIp()
        }
    }

    fun launch() = lifecycleScope.launch {
        setProxyRedsocks(proxyString.value)
        if (!running.value) {
            enableRedsocks()
        } else {
            reloadRedsocks()
        }
        checkIsRunning()
        saveProxyString(proxyString.value)
        checkIp()
    }

    fun isProxyStringValid(proxy: String): Boolean {
        val match = Regex("^([\\w+]+)://((.*):(.*)@)?([^@]+):(\\d+)\$").matchEntire(proxy) ?: return false
        val type = match.groups[1]!!.value
        if (!SUPPORTED_TYPES.contains(type)) return false
        return true
    }

    fun saveProxyString(proxy: String) {
        val res = listOf(proxy, *list.value.filter { it != proxy }.toTypedArray()).stream().limit(10).asSequence().toList()
        list.value = res
        prefs.edit { putStringSet("list", res.toSet()) }
    }

    fun clear() {
        list.value = emptyList()
        prefs.edit { putStringSet("list", null) }
    }

    fun checkIsRunning() {
        running.value = isRunning()
    }

    suspend fun checkIp() {
        val time = System.currentTimeMillis()
        status.value = getString(R.string.loading)
        speed.value = ""
        status.value = soax.checkIp()
        speed.value = "${System.currentTimeMillis()-time}ms"
    }
}