package com.cc.floconlite

import com.google.gson.Gson
import java.util.UUID

/**
 * Flocon `Analytics` plugin.
 *
 * Call [logEvent] wherever the app currently reports an analytics event
 * (Firebase, Umeng, custom tracker...) to mirror it to the Flocon desktop.
 */
object FloconAnalytics {

    private val gson = Gson()

    /**
     * Logs one analytics event to the Flocon desktop.
     *
     * @param tableId logical grouping, e.g. "firebase", "umeng"
     * @param eventName name of the event
     * @param properties event properties as simple key/value pairs
     */
    @JvmStatic
    @JvmOverloads
    fun logEvent(tableId: String, eventName: String, properties: Map<String, String> = emptyMap()) {
        if (!FloconLite.isStarted()) return

        val item = mapOf(
            "id" to UUID.randomUUID().toString(),
            "analyticsTableId" to tableId,
            "eventName" to eventName,
            "createdAt" to System.currentTimeMillis(),
            "properties" to properties.map { (name, value) -> mapOf("name" to name, "value" to value) }
        )

        FloconLite.send(
            plugin = "Analytics",
            method = "AddItems",
            bodyJson = gson.toJson(listOf(item))
        )
    }
}
