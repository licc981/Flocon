package com.cc.floconlite

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight Flocon protocol client.
 *
 * Implements just enough of the Flocon wire protocol (see the `protocol/` folder
 * of https://github.com/openflocon/Flocon) to let this app talk to the Flocon
 * Desktop app, without depending on any of Flocon's Gradle modules (which
 * require Kotlin 2.x / AGP 9.x and are incompatible with this project's
 * Kotlin 1.7 / AGP 7.x / Gradle 7.3 toolchain).
 *
 * Implements the `device`, `network`, `database`, `files`, `sharedPreferences`,
 * `Analytics`, `CrashReporter`, `Deeplink`, `featureflags`, `Table` and
 * `Dashboard` plugins.
 */
object FloconLite {

    const val PROTOCOL_VERSION = "1.0.0-lite"
    private const val TAG = "FloconLite"
    private const val WS_PORT = 9023
    private const val RECONNECT_DELAY_MS = 3000L

    private const val PLUGIN_DEVICE = "device"
    private const val METHOD_REGISTER_DEVICE = "registerDevice"

    private val gson = Gson()
    private val started = AtomicBoolean(false)

    private var appContext: Context? = null
    private var appName: String = "App"
    private var appPackageName: String = ""
    private var deviceId: String = ""
    private var deviceName: String = ""
    private var appInstance: Long = System.currentTimeMillis()

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    @Volatile
    private var isConnected: Boolean = false

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** plugin name -> handler(method, bodyJson) for Desktop -> Mobile messages. */
    private val incomingHandlers = mutableMapOf<String, (method: String, body: String) -> Unit>()

    init {
        registerIncomingHandler(PLUGIN_DEVICE) { method, _ ->
            if (method == "getAppIcon") {
                // App icon reporting is not implemented in this lite client.
            }
        }
        registerIncomingHandler("database") { method, body ->
            handleDatabaseMessage(method, body)
        }
        registerIncomingHandler("files") { method, body ->
            handleFilesMessage(method, body)
        }
        registerIncomingHandler("sharedPreferences") { method, body ->
            handleSharedPreferencesMessage(method, body)
        }
    }

    /**
     * Starts the Flocon-lite client. Call this once, typically from
     * `Application.onCreate()`, guarded by `BuildConfig.DEBUG`.
     *
     * Desktop reaches the device through `adb reverse tcp:9023 tcp:9023`,
     * so we always connect to localhost.
     */
    @JvmStatic
    @JvmOverloads
    fun install(context: Context, enabled: Boolean = true) {
        if (!enabled) return
        if (!started.compareAndSet(false, true)) return

        this.appContext = context.applicationContext
        this.appName = AppUtils.getAppName(context)
        this.appPackageName = AppUtils.getAppPackageName(context)
        this.deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        this.deviceId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }

        client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // websockets stay open
            .build()

        connect()
    }

    private fun connect() {
        val okHttpClient = client ?: return
        val request = Request.Builder()
            .url("ws://127.0.0.1:$WS_PORT/")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                isConnected = true
                Log.d(TAG, "Connected to Flocon desktop")
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                dispatchIncoming(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // not used, protocol uses text frames
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                isConnected = false
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!started.get()) return
        mainHandler.postDelayed({
            if (started.get() && !isConnected) {
                connect()
            }
        }, RECONNECT_DELAY_MS)
    }

    private fun onConnected() {
        registerDevice()
        appContext?.let { ctx ->
            FloconDatabase.refreshDatabaseList(ctx)
            FloconSharedPrefs.sendPreferencesList()
            FloconCrashReporter.flushPendingCrashes(ctx)
        }
    }

    private fun registerDevice() {
        send(
            plugin = PLUGIN_DEVICE,
            method = METHOD_REGISTER_DEVICE,
            bodyJson = gson.toJson(mapOf("serial" to deviceId))
        )
    }

    /**
     * Registers a handler for Desktop -> Mobile messages of a given plugin.
     * Only one handler per plugin is kept; calling this again replaces it.
     */
    @JvmStatic
    fun registerIncomingHandler(plugin: String, handler: (method: String, body: String) -> Unit) {
        incomingHandlers[plugin] = handler
    }

    private fun dispatchIncoming(text: String) {
        try {
            val json = JsonParser.parseString(text).asJsonObject
            val plugin = json.get("plugin")?.asString ?: return
            val method = json.get("method")?.asString ?: return
            val body = json.get("body")?.asString ?: ""
            incomingHandlers[plugin]?.invoke(method, body)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse incoming message from Flocon desktop", e)
        }
    }

    private fun handleDatabaseMessage(method: String, body: String) {
        val ctx = appContext ?: return
        when (method) {
            "getDatabases" -> FloconDatabase.refreshDatabaseList(ctx)
            "query" -> {
                val json = JsonParser.parseString(body).asJsonObject
                val requestId = json.get("requestId")?.asString ?: return
                val database = json.get("database")?.asString ?: return
                val sql = json.get("query")?.asString ?: return
                FloconDatabase.query(ctx, requestId, database, sql)
            }
        }
    }

    private fun handleFilesMessage(method: String, body: String) {
        val ctx = appContext ?: return
        val json = try {
            JsonParser.parseString(body).asJsonObject
        } catch (e: Exception) {
            return
        }
        val requestId = json.get("requestId")?.asString ?: return

        when (method) {
            "getRoots" -> FloconFiles.getRoots(ctx, requestId)
            "listFiles" -> FloconFiles.listFiles(
                ctx,
                requestId,
                json.get("path")?.asString ?: return,
                json.get("isConstantPath")?.asBoolean ?: false,
                json.get("withFoldersSize")?.asBoolean ?: false
            )
            "deleteFile" -> FloconFiles.deleteFile(
                ctx,
                requestId,
                json.get("parentPath")?.asString ?: return,
                json.get("filePath")?.asString ?: return,
                json.get("isConstantParentPath")?.asBoolean ?: false
            )
            "deleteFiles" -> FloconFiles.deleteFiles(
                ctx,
                requestId,
                json.get("parentPath")?.asString ?: return,
                json.getAsJsonArray("filePaths")?.map { it.asString } ?: emptyList(),
                json.get("isConstantParentPath")?.asBoolean ?: false
            )
            "deleteFolderContent" -> FloconFiles.deleteFolderContent(
                ctx,
                requestId,
                json.get("path")?.asString ?: return,
                json.get("isConstantPath")?.asBoolean ?: false
            )
            "getFile" -> FloconFiles.getFile(ctx, requestId, json.get("path")?.asString ?: return)
        }
    }

    private fun handleSharedPreferencesMessage(method: String, body: String) {
        val ctx = appContext ?: return
        when (method) {
            "getSharedPreferences" -> FloconSharedPrefs.sendPreferencesList()
            "getSharedPreferenceValue" -> {
                val json = JsonParser.parseString(body).asJsonObject
                val requestId = json.get("requestId")?.asString ?: return
                val name = json.get("sharedPreferenceName")?.asString ?: return
                FloconSharedPrefs.getPreferenceValues(ctx, requestId, name)
            }
            "setSharedPreferenceValue" -> {
                val json = JsonParser.parseString(body).asJsonObject
                val requestId = json.get("requestId")?.asString ?: return
                val name = json.get("sharedPreferenceName")?.asString ?: return
                val key = json.get("key")?.asString ?: return
                FloconSharedPrefs.setPreferenceValue(
                    ctx,
                    requestId,
                    name,
                    key,
                    json.get("stringValue")?.takeUnless { it.isJsonNull }?.asString,
                    json.get("intValue")?.takeUnless { it.isJsonNull }?.asInt,
                    json.get("longValue")?.takeUnless { it.isJsonNull }?.asLong,
                    json.get("floatValue")?.takeUnless { it.isJsonNull }?.asFloat,
                    json.get("booleanValue")?.takeUnless { it.isJsonNull }?.asBoolean,
                    json.getAsJsonArray("setStringValue")?.map { it.asString }
                )
            }
        }
    }

    /**
     * Sends a plugin message to the desktop. `bodyJson` must already be a JSON string
     * (it is embedded as-is inside the envelope's `body` field).
     */
    internal fun send(plugin: String, method: String, bodyJson: String) {
        val socket = webSocket ?: return
        if (!isConnected) return

        val envelope = OutgoingEnvelope(
            deviceId = deviceId,
            deviceName = deviceName,
            appName = appName,
            appPackageName = appPackageName,
            appInstance = appInstance,
            plugin = plugin,
            method = method,
            body = bodyJson
        )
        try {
            socket.send(gson.toJson(envelope))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send message to Flocon desktop", e)
        }
    }

    internal fun isStarted(): Boolean = started.get()
    internal fun currentDeviceId(): String = deviceId
    internal fun currentAppInstance(): Long = appInstance
    internal fun applicationContext(): Context? = appContext
}
