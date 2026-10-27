package com.cc.floconlite

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.util.UUID

/**
 * OkHttp interceptor implementing the Flocon `network` plugin protocol
 * (`logNetworkCallRequest` / `logNetworkCallResponse`).
 *
 * Add it near the end of the interceptor chain (as a regular application
 * interceptor) the same way you would add `HttpLoggingInterceptor`.
 */
class FloconInterceptor : Interceptor {

    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!FloconLite.isStarted()) {
            return chain.proceed(request)
        }

        val callId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        logRequest(callId, request, startTime)

        val startNanos = System.nanoTime()
        try {
            val response = chain.proceed(request)
            val durationMs = (System.nanoTime() - startNanos) / 1_000_000.0
            return logResponse(callId, response, durationMs)
        } catch (e: IOException) {
            val durationMs = (System.nanoTime() - startNanos) / 1_000_000.0
            logError(callId, durationMs, e)
            throw e
        }
    }

    private fun logRequest(callId: String, request: okhttp3.Request, startTime: Long) {
        val headers = LinkedHashMap<String, String>()
        for (name in request.headers.names()) {
            headers[name] = request.headers[name].orEmpty()
        }

        val requestBody = request.body
        val bodyString = try {
            if (requestBody != null && !bodyIsStreaming(requestBody)) {
                val buffer = okio.Buffer()
                requestBody.writeTo(buffer)
                buffer.readUtf8()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        val body = mapOf(
            "floconCallId" to callId,
            "floconNetworkType" to "http",
            "isMocked" to false,
            "url" to request.url.toString(),
            "method" to request.method,
            "startTime" to startTime,
            "requestHeaders" to headers,
            "requestBody" to bodyString,
            "requestSize" to (requestBody?.contentLength() ?: 0L)
        )

        FloconLite.send(
            plugin = "network",
            method = "logNetworkCallRequest",
            bodyJson = gson.toJson(body)
        )
    }

    private fun logResponse(callId: String, response: Response, durationMs: Double): Response {
        val responseBody = response.body
        val contentType = responseBody?.contentType()
        val isImage = contentType?.type == "image"

        var bodyString: String? = null
        var newResponse = response

        if (responseBody != null && !isImage) {
            try {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer.clone()
                bodyString = buffer.readString(charsetFor(contentType))
                // rebuild the body since we consumed the buffered source for inspection only
                val newBody = ResponseBody.create(contentType, responseBody.contentLength(), source)
                newResponse = response.newBuilder().body(newBody).build()
            } catch (e: Exception) {
                // if reading fails, keep original response untouched
            }
        }

        val headers = LinkedHashMap<String, String>()
        for (name in response.headers.names()) {
            headers[name] = response.headers[name].orEmpty()
        }

        val body = mapOf(
            "floconCallId" to callId,
            "floconNetworkType" to "http",
            "isMocked" to false,
            "durationMs" to durationMs,
            "responseHttpCode" to response.code,
            "responseGrpcStatus" to null,
            "responseContentType" to contentType?.toString(),
            "responseBody" to bodyString,
            "responseSize" to (responseBody?.contentLength() ?: 0L),
            "responseHeaders" to headers,
            "requestHeaders" to null,
            "responseError" to null,
            "isImage" to isImage
        )

        FloconLite.send(
            plugin = "network",
            method = "logNetworkCallResponse",
            bodyJson = gson.toJson(body)
        )

        return newResponse
    }

    private fun logError(callId: String, durationMs: Double, error: IOException) {
        val body = mapOf(
            "floconCallId" to callId,
            "floconNetworkType" to "http",
            "isMocked" to false,
            "durationMs" to durationMs,
            "responseHttpCode" to null,
            "responseGrpcStatus" to null,
            "responseContentType" to null,
            "responseBody" to null,
            "responseSize" to null,
            "responseHeaders" to emptyMap<String, String>(),
            "requestHeaders" to null,
            "responseError" to (error.message ?: error.toString()),
            "isImage" to false
        )

        FloconLite.send(
            plugin = "network",
            method = "logNetworkCallResponse",
            bodyJson = gson.toJson(body)
        )
    }

    private fun bodyIsStreaming(body: okhttp3.RequestBody): Boolean {
        return try {
            body.contentLength() < 0
        } catch (e: IOException) {
            true
        }
    }

    private fun charsetFor(mediaType: MediaType?): java.nio.charset.Charset {
        return mediaType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    }
}
