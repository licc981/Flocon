package com.cc.floconlite

import com.google.gson.Gson

/**
 * Flocon `featureflags` plugin.
 *
 * Reports the app's feature flags / experiments to the Flocon desktop
 * (read-only on the desktop side).
 */
object FloconFeatureFlags {

    private val gson = Gson()

    data class Flag(
        val name: String,
        val state: String,
        val variant: String,
        val payload: String? = null
    )

    /**
     * Registers all flags of a single provider (e.g. "firebase", "statsig").
     * This call replaces every flag previously sent for this provider.
     */
    @JvmStatic
    fun register(provider: String, flags: List<Flag>) {
        if (!FloconLite.isStarted()) return

        val body = mapOf(
            "provider" to provider,
            "flags" to flags.map {
                mapOf(
                    "name" to it.name,
                    "state" to it.state,
                    "variant" to it.variant,
                    "payload" to it.payload
                )
            }
        )

        FloconLite.send(
            plugin = "featureflags",
            method = "register",
            bodyJson = gson.toJson(body)
        )
    }
}
