package com.eight87.strictlykeptboy.caldav

import com.eight87.strictlykeptboy.caldav.auth.CalDavCredential
import com.eight87.strictlykeptboy.caldav.auth.authorizationHeader
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Phase Y — minimal CalDAV HTTP surface driven by OkHttp. The full
 * ical4j/dav4jvm wiring is deferred to SE-Q.10+ (the libs are declared
 * in `libs.versions.toml` per Y.1 for future adoption); this thin
 * wrapper covers OPTIONS / .well-known redirect / PROPFIND / REPORT /
 * GET / PUT / DELETE with ETag and is testable on JVM via OkHttp's
 * `MockWebServer`.
 *
 * Single-responsibility: this class only talks HTTP. Discovery state
 * machine lives in [discovery.CalDavDiscovery]; iCal parsing lives in
 * [ical.IcalParser].
 */
class CalDavHttp(
    private val client: OkHttpClient = OkHttpClient(),
    private val credential: CalDavCredential? = null,
) {
    /** Probe `DAV: calendar-access` header via an OPTIONS request. */
    fun options(url: String): CalDavOptionsResponse {
        val req = newRequest(url).method("OPTIONS", null).build()
        client.newCall(req).execute().use { resp ->
            return CalDavOptionsResponse(
                code = resp.code,
                davHeaders = resp.headers("DAV").flatMap { it.split(',').map(String::trim) },
                allow = resp.headers("Allow").flatMap { it.split(',').map(String::trim) },
            )
        }
    }

    /** PROPFIND with a depth header + body. Returns raw XML. */
    fun propfind(url: String, depth: String, body: String): CalDavXmlResponse {
        val req = newRequest(url)
            .method("PROPFIND", body.toRequestBody(XML))
            .header("Depth", depth)
            .build()
        return client.newCall(req).execute().use { it.toXml() }
    }

    /** RFC6578 `REPORT sync-collection`. */
    fun reportSync(url: String, body: String): CalDavXmlResponse {
        val req = newRequest(url)
            .method("REPORT", body.toRequestBody(XML))
            .header("Depth", "1")
            .build()
        return client.newCall(req).execute().use { it.toXml() }
    }

    /** GET an iCalendar resource; returns body + ETag. */
    fun getResource(url: String): CalDavResource {
        val req = newRequest(url).get().build()
        return client.newCall(req).execute().use { resp ->
            CalDavResource(
                code = resp.code,
                body = resp.body?.string().orEmpty(),
                etag = resp.header("ETag"),
            )
        }
    }

    /**
     * PUT an iCalendar resource. Honours `If-Match` for optimistic
     * concurrency; pass `etag = null` to force-overwrite via `If-Match: *`,
     * or pass `etag = ""` for a create (no precondition).
     */
    fun putResource(url: String, ical: String, etag: String?): CalDavResource {
        val builder = newRequest(url)
            .method("PUT", ical.toRequestBody(ICAL))
        when (etag) {
            null -> builder.header("If-Match", "*")
            "" -> builder.header("If-None-Match", "*")
            else -> builder.header("If-Match", etag)
        }
        return client.newCall(builder.build()).execute().use { resp ->
            CalDavResource(code = resp.code, body = resp.body?.string().orEmpty(), etag = resp.header("ETag"))
        }
    }

    /** DELETE an iCalendar resource with `If-Match` for stale-detection. */
    fun deleteResource(url: String, etag: String?): Int {
        val builder = newRequest(url).delete()
        if (etag != null) builder.header("If-Match", etag) else builder.header("If-Match", "*")
        return client.newCall(builder.build()).execute().use { it.code }
    }

    private fun newRequest(url: String): Request.Builder {
        val b = Request.Builder().url(url)
        credential?.let { b.header("Authorization", it.authorizationHeader()) }
        b.header("User-Agent", "strictlykeptboy-caldav/1")
        return b
    }

    private fun Response.toXml() = CalDavXmlResponse(
        code = code,
        xml = body?.string().orEmpty(),
        location = header("Location"),
    )

    companion object {
        private val XML = "application/xml; charset=utf-8".toMediaTypeOrNull()
        private val ICAL = "text/calendar; charset=utf-8".toMediaTypeOrNull()
    }
}

data class CalDavOptionsResponse(val code: Int, val davHeaders: List<String>, val allow: List<String>) {
    val supportsCalendar: Boolean get() = davHeaders.any { it.equals("calendar-access", ignoreCase = true) }
    val supportsSyncCollection: Boolean get() = davHeaders.any { it.equals("sync-collection", ignoreCase = true) }
}

data class CalDavXmlResponse(val code: Int, val xml: String, val location: String?)
data class CalDavResource(val code: Int, val body: String, val etag: String?)
