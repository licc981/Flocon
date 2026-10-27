package com.cc.floconlite

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.google.gson.Gson

/**
 * Flocon `database` plugin.
 *
 * Exposes the app's SQLite databases (found in the standard `databases` folder)
 * for browsing and running read/write SQL queries from the Flocon desktop.
 *
 * Works with plain SQLite / SQLiteOpenHelper. No Room dependency required.
 */
object FloconDatabase {

    private val gson = Gson()
    private val openDatabases = mutableMapOf<String, SQLiteDatabase>()

    /**
     * Scans the app's `databases` folder and reports the list to the desktop.
     * Call this once Flocon is started, and again whenever a database is created.
     */
    @JvmStatic
    fun refreshDatabaseList(context: Context) {
        if (!FloconLite.isStarted()) return

        val dbFolder = context.getDatabasePath("x").parentFile
        val databases = dbFolder?.listFiles { file ->
            file.isFile && !file.name.endsWith("-journal") && !file.name.endsWith("-wal") && !file.name.endsWith("-shm")
        }?.map { mapOf("id" to it.name, "name" to it.name) }.orEmpty()

        FloconLite.send(
            plugin = "database",
            method = "getDatabases",
            bodyJson = gson.toJson(databases)
        )
    }

    internal fun query(context: Context, requestId: String, databaseId: String, sql: String) {
        val result = try {
            val db = openDatabase(context, databaseId)
            val queryResult = executeSql(db, sql)
            Log.d("FloconDatabase", "success query: $sql")
            queryResult
        } catch (e: Exception) {
            Log.w("FloconDatabase", "query failed: ${e.message}", e)
            wrapError(e.message ?: e.toString(), sql)
        }

        val response = mapOf("requestId" to requestId, "result" to gson.toJson(result))
        FloconLite.send(
            plugin = "database",
            method = "query",
            bodyJson = gson.toJson(response)
        )
    }

    private fun openDatabase(context: Context, databaseId: String): SQLiteDatabase {
        openDatabases[databaseId]?.let { if (it.isOpen) return it }

        val path = context.getDatabasePath(databaseId).absolutePath
        // Plain read/write open, no CREATE_IF_NECESSARY so we never silently open
        // an empty db at the wrong path. If the app's own connection runs in WAL
        // mode, this separate connection still sees all committed data since SQLite
        // WAL allows concurrent readers; we don't need to request WAL ourselves
        // (doing so via the legacy int-flags overload can fight the app's own
        // connection for the WAL file lock and stall).
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE)
        openDatabases[databaseId] = db
        return db
    }

    private fun executeSql(db: SQLiteDatabase, sql: String): Map<String, String> {
        val trimmed = sql.trim().trimEnd(';').trim()
        val firstWord = trimmed.substringBefore(' ').lowercase()

        return when (firstWord) {
            "select", "pragma", "with", "explain" -> {
                db.rawQuery(sql, null).use { cursor ->
                    wrapSelect(cursor)
                }
            }
            "insert" -> {
                db.execSQL(sql)
                val insertedId = db.rawQuery("SELECT last_insert_rowid()", null).use {
                    if (it.moveToFirst()) it.getLong(0) else -1L
                }
                mapOf("type" to "Insert", "body" to gson.toJson(mapOf("insertedId" to insertedId)))
            }
            "update", "delete" -> {
                val before = changesCount(db)
                db.execSQL(sql)
                val affected = changesCount(db) - before
                mapOf("type" to "UpdateDelete", "body" to gson.toJson(mapOf("affectedCount" to affected)))
            }
            else -> {
                db.execSQL(sql)
                mapOf("type" to "RawSuccess", "body" to "{}")
            }
        }
    }

    private fun wrapSelect(cursor: Cursor): Map<String, String> {
        val columns = cursor.columnNames.toList()
        val values = mutableListOf<List<String?>>()
        while (cursor.moveToNext()) {
            val row = columns.indices.map { index ->
                if (cursor.isNull(index)) null else cursor.getString(index)
            }
            values.add(row)
        }
        val body = mapOf("columns" to columns, "values" to values)
        return mapOf("type" to "Select", "body" to gson.toJson(body))
    }

    private fun wrapError(message: String, sql: String): Map<String, String> {
        val body = mapOf("message" to message, "originalSql" to sql)
        return mapOf("type" to "Error", "body" to gson.toJson(body))
    }

    private fun changesCount(db: SQLiteDatabase): Long {
        return db.rawQuery("SELECT total_changes()", null).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    private inline fun <T> Cursor.use(block: (Cursor) -> T): T {
        try {
            return block(this)
        } finally {
            close()
        }
    }
}
