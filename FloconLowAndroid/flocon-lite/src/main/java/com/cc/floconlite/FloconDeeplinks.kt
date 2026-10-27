package com.cc.floconlite

import com.google.gson.Gson

/**
 * Flocon `Deeplink` plugin.
 *
 * Declares the app's deeplinks so they can be triggered from the Flocon
 * desktop (the desktop launches them through adb, no message is sent back
 * to the app for this plugin).
 */
object FloconDeeplinks {

    private val gson = Gson()

    /**
     * A single deeplink parameter. Use [autoComplete] for a fixed list of
     * suggested values, or [variableName] to bind to a shared variable
     * declared with [registerDeeplinks]'s `variables` parameter.
     */
    data class Parameter(
        val name: String,
        val autoComplete: List<String>? = null,
        val variableName: String? = null
    )

    data class Deeplink(
        val link: String,
        val label: String? = null,
        val description: String? = null,
        val parameters: List<Parameter> = emptyList()
    )

    data class Variable(
        val name: String,
        val description: String? = null,
        val suggestions: List<String>? = null
    )

    /**
     * Registers the app's deeplinks with the desktop. Call again whenever the
     * list changes; this replaces the full list on the desktop side.
     */
    @JvmStatic
    @JvmOverloads
    fun registerDeeplinks(deeplinks: List<Deeplink>, variables: List<Variable> = emptyList()) {
        if (!FloconLite.isStarted()) return

        val body = mapOf(
            "deeplinks" to deeplinks.map { link ->
                mapOf(
                    "link" to link.link,
                    "label" to link.label,
                    "description" to link.description,
                    "parameters" to link.parameters.map { param ->
                        when {
                            param.variableName != null -> mapOf(
                                "type" to "variable",
                                "name" to param.name,
                                "variableName" to param.variableName
                            )
                            else -> mapOf(
                                "type" to "auto_complete",
                                "name" to param.name,
                                "autoComplete" to (param.autoComplete ?: emptyList<String>())
                            )
                        }
                    }
                )
            },
            "variables" to variables.map { variable ->
                mapOf(
                    "name" to variable.name,
                    "description" to variable.description,
                    "mode" to if (variable.suggestions != null) {
                        mapOf("type" to "auto_complete", "suggestions" to variable.suggestions)
                    } else {
                        null
                    }
                )
            }
        )

        FloconLite.send(
            plugin = "Deeplink",
            method = "GetDeeplinks",
            bodyJson = gson.toJson(body)
        )
    }
}
