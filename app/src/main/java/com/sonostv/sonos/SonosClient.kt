package com.sonostv.sonos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

const val SONOS_PORT = 1400

enum class SonosService(val path: String, val type: String) {
    AvTransport("/MediaRenderer/AVTransport/Control", "urn:schemas-upnp-org:service:AVTransport:1"),
    Rendering("/MediaRenderer/RenderingControl/Control", "urn:schemas-upnp-org:service:RenderingControl:1"),
    GroupRendering("/MediaRenderer/GroupRenderingControl/Control", "urn:schemas-upnp-org:service:GroupRenderingControl:1"),
    ContentDirectory("/MediaServer/ContentDirectory/Control", "urn:schemas-upnp-org:service:ContentDirectory:1"),
    ZoneGroupTopology("/ZoneGroupTopology/Control", "urn:schemas-upnp-org:service:ZoneGroupTopology:1"),
}

/**
 * Talks to Sonos players over their local UPnP/SOAP endpoints on port 1400.
 * No cloud account or internet connection is involved.
 */
class SonosClient(
    private val http: OkHttpClient = defaultHttpClient(),
) {

    suspend fun soap(
        host: String,
        service: SonosService,
        action: String,
        args: List<Pair<String, String>> = emptyList(),
    ): String = withContext(Dispatchers.IO) {
        val body = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
            append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
            append("<u:").append(action).append(" xmlns:u=\"").append(service.type).append("\">")
            for ((name, value) in args) {
                append('<').append(name).append('>')
                append(Xml.escape(value))
                append("</").append(name).append('>')
            }
            append("</u:").append(action).append(">")
            append("</s:Body></s:Envelope>")
        }

        val request = Request.Builder()
            .url("http://$host:$SONOS_PORT${service.path}")
            .addHeader("SOAPACTION", "\"${service.type}#$action\"")
            .post(body.toRequestBody(SOAP_MEDIA_TYPE))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val code = Xml.text(text, "errorCode")
                throw SonosException("$action failed: HTTP ${response.code}${code?.let { " (UPnP $it)" } ?: ""}")
            }
            text
        }
    }

    // ---- Transport ---------------------------------------------------------

    suspend fun play(host: String) {
        soap(host, SonosService.AvTransport, "Play", listOf("InstanceID" to "0", "Speed" to "1"))
    }

    suspend fun pause(host: String) {
        soap(host, SonosService.AvTransport, "Pause", listOf("InstanceID" to "0"))
    }

    suspend fun next(host: String) {
        soap(host, SonosService.AvTransport, "Next", listOf("InstanceID" to "0"))
    }

    suspend fun previous(host: String) {
        soap(host, SonosService.AvTransport, "Previous", listOf("InstanceID" to "0"))
    }

    suspend fun seekToMillis(host: String, positionMs: Long) {
        soap(
            host, SonosService.AvTransport, "Seek",
            listOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to formatSeekTime(positionMs)),
        )
    }

    /** [queuePosition] is 1-based, matching the `Track` value reported by GetPositionInfo. */
    suspend fun seekToQueuePosition(host: String, queuePosition: Int) {
        soap(
            host, SonosService.AvTransport, "Seek",
            listOf("InstanceID" to "0", "Unit" to "TRACK_NR", "Target" to queuePosition.toString()),
        )
    }

    suspend fun fetchTransport(host: String): Transport {
        val transportInfo = soap(
            host, SonosService.AvTransport, "GetTransportInfo",
            listOf("InstanceID" to "0"),
        )
        val state = PlayState.from(Xml.text(transportInfo, "CurrentTransportState"))

        val positionInfo = soap(
            host, SonosService.AvTransport, "GetPositionInfo",
            listOf("InstanceID" to "0"),
        )
        val trackUri = Xml.text(positionInfo, "TrackURI")
        val isStream = trackUri != null && STREAM_URI_PREFIXES.any { trackUri.startsWith(it) }
        val metadata = Xml.text(positionInfo, "TrackMetaData")
        val parsed = Didl.parseItems(metadata, host).firstOrNull()
        val streamContent = metadata?.let { Didl.streamContent(it) }

        var track = parsed ?: Track(null, null, null, null)
        track = track.copy(queuePosition = Xml.int(positionInfo, "Track") ?: 0)

        var source: String? = null
        if (isStream) {
            // For radio the useful "now playing" text lives in r:streamContent, and the
            // station name lives in the enclosing media metadata rather than the track.
            val mediaInfo = runCatching {
                soap(host, SonosService.AvTransport, "GetMediaInfo", listOf("InstanceID" to "0"))
            }.getOrNull()
            val stationMeta = mediaInfo?.let { Xml.text(it, "CurrentURIMetaData") }
            source = stationMeta?.let { Didl.parseItems(it, host).firstOrNull()?.title }
            val (streamArtist, streamTitle) = splitStreamContent(streamContent)
            track = track.copy(
                title = streamTitle ?: source ?: track.title,
                artist = streamArtist,
                album = if (streamTitle != null) source else null,
                artUrl = track.artUrl ?: stationMeta?.let { Didl.parseItems(it, host).firstOrNull()?.artUrl },
            )
        }

        return Transport(
            state = state,
            track = track,
            positionMs = parseUpnpDuration(Xml.text(positionInfo, "RelTime")),
            durationMs = if (isStream) 0L else parseUpnpDuration(Xml.text(positionInfo, "TrackDuration")),
            isStream = isStream,
            source = source,
        )
    }

    // ---- Volume ------------------------------------------------------------

    suspend fun fetchVolume(host: String): Pair<Int, Boolean> {
        val response = soap(
            host, SonosService.GroupRendering, "GetGroupVolume",
            listOf("InstanceID" to "0"),
        )
        val muteResponse = soap(
            host, SonosService.GroupRendering, "GetGroupMute",
            listOf("InstanceID" to "0"),
        )
        return (Xml.int(response, "CurrentVolume") ?: 0) to (Xml.text(muteResponse, "CurrentMute") == "1")
    }

    suspend fun adjustVolume(host: String, delta: Int): Int {
        val response = soap(
            host, SonosService.GroupRendering, "SetRelativeGroupVolume",
            listOf("InstanceID" to "0", "Adjustment" to delta.toString()),
        )
        return Xml.int(response, "NewVolume") ?: 0
    }

    suspend fun setMute(host: String, muted: Boolean) {
        soap(
            host, SonosService.GroupRendering, "SetGroupMute",
            listOf("InstanceID" to "0", "DesiredMute" to if (muted) "1" else "0"),
        )
    }

    // ---- Queue -------------------------------------------------------------

    suspend fun fetchQueue(host: String, limit: Int = 200): List<Track> {
        val response = soap(
            host, SonosService.ContentDirectory, "Browse",
            listOf(
                "ObjectID" to "Q:0",
                "BrowseFlag" to "BrowseDirectChildren",
                "Filter" to "*",
                "StartingIndex" to "0",
                "RequestedCount" to limit.toString(),
                "SortCriteria" to "",
            ),
        )
        return Didl.parseItems(Xml.text(response, "Result"), host)
    }

    // ---- Topology ----------------------------------------------------------

    suspend fun fetchGroups(host: String): List<SonosGroup> {
        val response = soap(host, SonosService.ZoneGroupTopology, "GetZoneGroupState")
        val state = Xml.text(response, "ZoneGroupState") ?: return emptyList()
        return Topology.parseGroups(state)
    }

    companion object {
        private val SOAP_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()

        private val STREAM_URI_PREFIXES = listOf(
            "x-sonosapi-stream:",
            "x-sonosapi-radio:",
            "x-sonosapi-hls:",
            "x-rincon-mp3radio:",
            "aac:",
            "hls-radio:",
        )

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        private fun formatSeekTime(millis: Long): String {
            val totalSeconds = (millis / 1000).coerceAtLeast(0)
            return String.format(
                "%d:%02d:%02d",
                totalSeconds / 3600,
                (totalSeconds % 3600) / 60,
                totalSeconds % 60,
            )
        }

        /** Radio streams pack both fields into one string, usually as "Artist - Title". */
        private fun splitStreamContent(content: String?): Pair<String?, String?> {
            val trimmed = content?.trim().orNullIfBlank() ?: return null to null
            val separator = trimmed.indexOf(" - ")
            return if (separator > 0) {
                trimmed.take(separator) to trimmed.substring(separator + 3).orNullIfBlank()
            } else {
                null to trimmed
            }
        }
    }
}

class SonosException(message: String) : Exception(message)
