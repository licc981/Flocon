package com.cc.floconlite

/**
 * Mobile -> Desktop envelope.
 * See protocol/README.md in the Flocon repository.
 */
internal data class OutgoingEnvelope(
    val deviceId: String,
    val deviceName: String,
    val appName: String,
    val appPackageName: String,
    val appInstance: Long,
    val platform: String = "android",
    val versionName: String = FloconLite.PROTOCOL_VERSION,
    val plugin: String,
    val method: String,
    val body: String
)

/**
 * Desktop -> Mobile envelope.
 */
internal data class IncomingEnvelope(
    val plugin: String? = null,
    val method: String? = null,
    val body: String? = null
)
