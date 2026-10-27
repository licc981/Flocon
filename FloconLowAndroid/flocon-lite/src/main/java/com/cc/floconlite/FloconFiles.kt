package com.cc.floconlite

import android.content.Context
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Flocon `files` plugin.
 *
 * Exposes the app's root folders (files dir, cache dir, databases dir) for
 * browsing / deleting / downloading from the Flocon desktop.
 */
object FloconFiles {

    private const val UPLOAD_PORT = 9024
    private const val ID_FILES = "files"
    private const val ID_CACHES = "caches"
    private const val ID_DATABASES = "databases"

    private val gson = Gson()
    private val uploadClient = OkHttpClient()

    internal fun getRoots(context: Context, requestId: String) {
        val roots = mutableListOf<Map<String, String>>()

        context.filesDir?.let {
            roots.add(mapOf("id" to ID_FILES, "name" to "Files", "path" to it.absolutePath))
        }
        context.cacheDir?.let {
            roots.add(mapOf("id" to ID_CACHES, "name" to "Caches", "path" to it.absolutePath))
        }
        context.getDatabasePath("x").parentFile?.let {
            if (it.exists()) {
                roots.add(mapOf("id" to ID_DATABASES, "name" to "Databases", "path" to it.absolutePath))
            }
        }

        val response = mapOf("requestId" to requestId, "roots" to roots)
        FloconLite.send(
            plugin = "files",
            method = "getroots",
            bodyJson = gson.toJson(response)
        )
    }

    internal fun listFiles(
        context: Context,
        requestId: String,
        path: String,
        isConstantPath: Boolean,
        withFoldersSize: Boolean
    ) {
        val folder = resolvePath(context, path, isConstantPath)
        sendFileList(requestId, folder, withFoldersSize)
    }

    internal fun deleteFile(
        context: Context,
        requestId: String,
        parentPath: String,
        filePath: String,
        isConstantParentPath: Boolean
    ) {
        try {
            File(filePath).deleteRecursively()
        } catch (e: Exception) {
            // ignore, still send back current content
        }
        val folder = resolvePath(context, parentPath, isConstantParentPath)
        sendFileList(requestId, folder, false)
    }

    internal fun deleteFiles(
        context: Context,
        requestId: String,
        parentPath: String,
        filePaths: List<String>,
        isConstantParentPath: Boolean
    ) {
        for (path in filePaths) {
            try {
                File(path).deleteRecursively()
            } catch (e: Exception) {
                // ignore
            }
        }
        val folder = resolvePath(context, parentPath, isConstantParentPath)
        sendFileList(requestId, folder, false)
    }

    internal fun deleteFolderContent(context: Context, requestId: String, path: String, isConstantPath: Boolean) {
        val folder = resolvePath(context, path, isConstantPath)
        folder.listFiles()?.forEach {
            try {
                it.deleteRecursively()
            } catch (e: Exception) {
                // ignore
            }
        }
        sendFileList(requestId, folder, false)
    }

    internal fun getFile(context: Context, requestId: String, path: String) {
        val file = File(path)
        if (!file.exists() || !file.isFile) return

        Thread {
            try {
                val url = "http://127.0.0.1:$UPLOAD_PORT/upload"
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("remotePath", path)
                    .addFormDataPart("requestId", requestId)
                    .addFormDataPart("deviceId", FloconLite.currentDeviceId())
                    .addFormDataPart("appPackageName", context.packageName)
                    .addFormDataPart("appInstance", FloconLite.currentAppInstance().toString())
                    .addFormDataPart(
                        "file",
                        file.name,
                        file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Flocon")
                    .post(body)
                    .build()

                uploadClient.newCall(request).execute().close()
            } catch (e: Exception) {
                // upload failed, nothing more we can do
            }
        }.start()
    }

    private fun sendFileList(requestId: String, folder: File, withFoldersSize: Boolean) {
        val files = folder.listFiles()?.map { f ->
            mapOf(
                "name" to f.name,
                "path" to f.absolutePath,
                "isDirectory" to f.isDirectory,
                "size" to if (f.isDirectory) (if (withFoldersSize) folderSize(f) else 0L) else f.length(),
                "lastModified" to f.lastModified()
            )
        }.orEmpty()

        val response = mapOf("requestId" to requestId, "files" to files)
        FloconLite.send(
            plugin = "files",
            method = "listFiles",
            bodyJson = gson.toJson(response)
        )
    }

    private fun folderSize(folder: File): Long {
        var size = 0L
        folder.listFiles()?.forEach {
            size += if (it.isDirectory) folderSize(it) else it.length()
        }
        return size
    }

    private fun resolvePath(context: Context, path: String, isConstantPath: Boolean): File {
        if (!isConstantPath) return File(path)
        return when (path) {
            ID_FILES -> context.filesDir
            ID_CACHES -> context.cacheDir
            ID_DATABASES -> context.getDatabasePath("x").parentFile ?: context.filesDir
            else -> File(path)
        }
    }
}
