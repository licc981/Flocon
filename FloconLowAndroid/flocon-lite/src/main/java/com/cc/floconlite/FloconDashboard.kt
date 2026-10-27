package com.cc.floconlite

import com.google.gson.Gson
import com.google.gson.JsonParser

/**
 * Flocon `Dashboard` plugin.
 *
 * Lets the app expose a custom, interactive debug panel (sections, forms,
 * buttons, text fields, checkboxes...) shown in the Flocon desktop app.
 */
object FloconDashboard {

    private val gson = Gson()

    private val onClickListeners = mutableMapOf<String, () -> Unit>()
    private val onFormSubmittedListeners = mutableMapOf<String, (Map<String, String>) -> Unit>()
    private val onTextFieldSubmittedListeners = mutableMapOf<String, (String) -> Unit>()
    private val onCheckBoxChangedListeners = mutableMapOf<String, (Boolean) -> Unit>()

    init {
        FloconLite.registerIncomingHandler("Dashboard") { method, body ->
            handleIncoming(method, body)
        }
    }

    /** Builder entry point. `dashboardId` identifies this dashboard on the desktop. */
    fun dashboard(dashboardId: String, block: DashboardBuilder.() -> Unit) {
        val builder = DashboardBuilder(dashboardId)
        builder.block()
        builder.publish()
    }

    private fun handleIncoming(method: String, body: String) {
        when (method) {
            "onClick" -> {
                // body is the raw button id, not JSON
                onClickListeners[body]?.invoke()
            }
            "onFormSubmitted" -> {
                val json = JsonParser.parseString(body).asJsonObject
                val formId = json.get("id")?.asString ?: return
                val values = mutableMapOf<String, String>()
                json.getAsJsonObject("values")?.entrySet()?.forEach { (k, v) ->
                    values[k] = v.asString
                }
                onFormSubmittedListeners[formId]?.invoke(values)
            }
            "onTextFieldSubmitted" -> {
                val json = JsonParser.parseString(body).asJsonObject
                val id = json.get("id")?.asString ?: return
                val value = json.get("value")?.asString ?: ""
                onTextFieldSubmittedListeners[id]?.invoke(value)
            }
            "onCheckBoxValueChanged" -> {
                val json = JsonParser.parseString(body).asJsonObject
                val id = json.get("id")?.asString ?: return
                val value = json.get("value")?.asBoolean ?: false
                onCheckBoxChangedListeners[id]?.invoke(value)
            }
        }
    }

    class DashboardBuilder(private val dashboardId: String) {
        private val containers = mutableListOf<MutableMap<String, Any?>>()

        /** A simple, non-interactive section. */
        fun section(name: String, block: SectionBuilder.() -> Unit) {
            val builder = SectionBuilder(dashboardId)
            builder.block()
            containers.add(
                mutableMapOf(
                    "name" to name,
                    "elements" to builder.elements,
                    "containerConfig" to mapOf("containerType" to "SECTION")
                )
            )
        }

        /** A form: text fields / checkboxes are collected and submitted together via [submitText] button. */
        fun form(name: String, formId: String, submitText: String = "Submit", block: SectionBuilder.() -> Unit) {
            val builder = SectionBuilder(dashboardId)
            builder.block()
            containers.add(
                mutableMapOf(
                    "name" to name,
                    "elements" to builder.elements,
                    "containerConfig" to mapOf(
                        "containerType" to "FORM",
                        "formId" to "${dashboardId}_$formId",
                        "submitText" to submitText
                    )
                )
            )
        }

        internal fun publish() {
            val body = mapOf(
                "dashboardId" to dashboardId,
                "containers" to containers
            )
            FloconLite.send(
                plugin = "Dashboard",
                method = "update",
                bodyJson = Gson().toJson(body)
            )
        }
    }

    class SectionBuilder(private val dashboardId: String) {
        internal val elements = mutableListOf<Map<String, Any?>>()

        fun label(text: String, color: Int? = null) {
            elements.add(mapOf("label" to mapOf("label" to text, "color" to color)))
        }

        fun text(label: String, value: String, color: Int? = null) {
            elements.add(mapOf("text" to mapOf("label" to label, "value" to value, "color" to color)))
        }

        fun plainText(label: String, value: String, isJson: Boolean = false) {
            elements.add(
                mapOf(
                    "plainText" to mapOf(
                        "label" to label,
                        "value" to value,
                        "type" to if (isJson) "json" else "text"
                    )
                )
            )
        }

        fun markdown(label: String, value: String) {
            elements.add(mapOf("markdown" to mapOf("label" to label, "value" to value)))
        }

        fun html(label: String, value: String) {
            elements.add(mapOf("html" to mapOf("label" to label, "value" to value)))
        }

        /** Adds a button. [onClick] is invoked when the button is pressed on the desktop. */
        fun button(id: String, text: String, onClick: () -> Unit) {
            val fullId = "${dashboardId}_$id"
            onClickListeners[fullId] = onClick
            elements.add(mapOf("button" to mapOf("id" to fullId, "text" to text)))
        }

        /**
         * Adds a text field. Inside a `form { }`, its value is collected with the form submit.
         * [onSubmit] (if provided) also fires immediately when the field is submitted standalone.
         */
        fun textField(id: String, label: String, value: String, placeHolder: String? = null, onSubmit: ((String) -> Unit)? = null) {
            val fullId = "${dashboardId}_$id"
            if (onSubmit != null) {
                onTextFieldSubmittedListeners[fullId] = onSubmit
            }
            elements.add(
                mapOf(
                    "textField" to mapOf(
                        "id" to fullId,
                        "label" to label,
                        "placeHolder" to placeHolder,
                        "value" to value
                    )
                )
            )
        }

        /**
         * Adds a checkbox. [onChanged] (if provided) fires immediately when toggled standalone,
         * otherwise (inside a `form { }`) its value is collected with the form submit.
         */
        fun checkBox(id: String, label: String, value: Boolean, onChanged: ((Boolean) -> Unit)? = null) {
            val fullId = "${dashboardId}_$id"
            if (onChanged != null) {
                onCheckBoxChangedListeners[fullId] = onChanged
            }
            elements.add(mapOf("checkBox" to mapOf("id" to fullId, "label" to label, "value" to value)))
        }
    }
}
