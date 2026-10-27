package com.cc.floconlite

import android.content.Context
import com.google.gson.Gson

/**
 * Flocon `sharedPreferences` plugin.
 *
 * Automatically tracks SharedPreferences files opened via the context's
 * getSharedPreferences calls and reports them to Flocon desktop for inspection/editing.
 */
object FloconSharedPrefs {

    private val gson = Gson()
    private val trackedNames = mutableSetOf<String>()

    /**
     * Register a SharedPreferences file name for Flocon desktop visibility.
     * Call this whenever you open a new SP file (or call registerAllFromContext
     * to auto-scan all opened files in a Context).
     */
    @JvmStatic
    fun registerPreference(name: String) {
        if (!FloconLite.isStarted()) return
        if (trackedNames.add(name)) {
            sendPreferencesList()
        }
    }

    /**
     * Auto-register the main "spUtils" preferences file that SPUtils.getInstance()
     * uses by default. Call this once from Application.onCreate() after FloconLite.install().
     */
    @JvmStatic
    fun registerDefaultSpUtils(name:String) {
        // SPUtils.getInstance() defaults to "spUtils" name
        registerPreference(name)
    }

    internal fun sendPreferencesList() {
        val list = trackedNames.sorted().map { mapOf("name" to it) }
        FloconLite.send(
            plugin = "sharedPreferences",
            method = "getSharedPreferences",
            bodyJson = gson.toJson(list)
        )
    }

    internal fun getPreferenceValues(
        context: Context,
        requestId: String,
        sharedPreferenceName: String
    ) {
        try {
            val prefs = context.getSharedPreferences(sharedPreferenceName, Context.MODE_PRIVATE)
            val rows = prefs.all.map { (key, value) ->
                buildPreferenceRow(key, value)
            }

            val response = mapOf(
                "requestId" to requestId,
                "sharedPreferenceName" to sharedPreferenceName,
                "rows" to rows
            )

            FloconLite.send(
                plugin = "sharedPreferences",
                method = "getSharedPreferenceValue",
                bodyJson = gson.toJson(response)
            )
        } catch (e: Exception) {
            val response = mapOf(
                "requestId" to requestId,
                "sharedPreferenceName" to sharedPreferenceName,
                "rows" to emptyList<Any>()
            )
            FloconLite.send(
                plugin = "sharedPreferences",
                method = "getSharedPreferenceValue",
                bodyJson = gson.toJson(response)
            )
        }
    }

    internal fun setPreferenceValue(
        context: Context,
        requestId: String,
        sharedPreferenceName: String,
        key: String,
        stringValue: String?,
        intValue: Int?,
        longValue: Long?,
        floatValue: Float?,
        booleanValue: Boolean?,
        setStringValue: List<String>?
    ) {
        try {
            val prefs = context.getSharedPreferences(sharedPreferenceName, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            when {
                stringValue != null -> editor.putString(key, stringValue)
                intValue != null -> editor.putInt(key, intValue)
                longValue != null -> editor.putLong(key, longValue)
                floatValue != null -> editor.putFloat(key, floatValue)
                booleanValue != null -> editor.putBoolean(key, booleanValue)
                setStringValue != null -> editor.putStringSet(key, setStringValue.toSet())
            }

            editor.apply()
            getPreferenceValues(context, requestId, sharedPreferenceName)
        } catch (e: Exception) {
            getPreferenceValues(context, requestId, sharedPreferenceName)
        }
    }

    private fun buildPreferenceRow(key: String, value: Any?): Map<String, Any?> {
        return when (value) {
            is String -> mapOf("key" to key, "stringValue" to value)
            is Int -> mapOf("key" to key, "intValue" to value)
            is Long -> mapOf("key" to key, "longValue" to value)
            is Float -> mapOf("key" to key, "floatValue" to value)
            is Boolean -> mapOf("key" to key, "booleanValue" to value)
            is Set<*> -> {
                @Suppress("UNCHECKED_CAST")
                val stringSet = (value as? Set<String>) ?: emptySet()
                mapOf("key" to key, "setStringValue" to stringSet.toList())
            }
            else -> mapOf("key" to key, "stringValue" to value.toString())
        }
    }
}
