package com.cc.floconlite

import com.google.gson.Gson
import java.util.UUID

/**
 * Flocon `Table` plugin.
 *
 * Lets the app stream arbitrary rows of data (grouped by table name) to the
 * Flocon desktop for inspection, e.g. internal state snapshots, queue
 * contents, feature usage counters...
 */
object FloconTables {

    private val gson = Gson()

    /**
     * Adds one row to a table on the desktop.
     *
     * @param tableName logical table name, rows are grouped by it
     * @param columns column name -> cell value (as string)
     */
    @JvmStatic
    fun addRow(tableName: String, columns: Map<String, String>) {
        if (!FloconLite.isStarted()) return

        val item = mapOf(
            "id" to UUID.randomUUID().toString(),
            "name" to tableName,
            "createdAt" to System.currentTimeMillis(),
            "columns" to columns.map { (column, value) -> mapOf("column" to column, "value" to value) }
        )

        FloconLite.send(
            plugin = "Table",
            method = "AddItems",
            bodyJson = gson.toJson(listOf(item))
        )
    }
}
