package com.sonostv.sonos

import android.util.Xml as AndroidXml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** Parser for the DIDL-Lite payloads Sonos returns for track metadata and queue listings. */
object Didl {

    fun parseItems(didl: String?, host: String): List<Track> {
        if (didl.isNullOrBlank()) return emptyList()
        val items = mutableListOf<Track>()
        try {
            val parser = AndroidXml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(StringReader(didl))
            }

            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var art: String? = null
            var depth = 0

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "item" -> {
                            depth = parser.depth
                            title = null; artist = null; album = null; art = null
                        }
                        "dc:title" -> if (depth > 0) title = parser.nextText().orNullIfBlank()
                        "dc:creator" -> if (depth > 0) artist = parser.nextText().orNullIfBlank()
                        "upnp:artist" -> if (depth > 0 && artist == null) artist = parser.nextText().orNullIfBlank()
                        "upnp:album" -> if (depth > 0) album = parser.nextText().orNullIfBlank()
                        "upnp:albumArtURI" -> if (depth > 0) art = parser.nextText().orNullIfBlank()
                    }

                    XmlPullParser.END_TAG -> if (parser.name == "item" && depth > 0) {
                        items += Track(
                            title = title,
                            artist = artist,
                            album = album,
                            artUrl = resolveArtUrl(art, host),
                        )
                        depth = 0
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            return items
        }
        return items
    }

    fun streamContent(didl: String): String? =
        Xml.text(didl, "streamContent")

    /** Album art URIs are usually paths relative to the player's own HTTP server. */
    private fun resolveArtUrl(uri: String?, host: String): String? {
        val value = uri?.trim().orNullIfBlank() ?: return null
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("/") -> "http://$host:$SONOS_PORT$value"
            else -> "http://$host:$SONOS_PORT/$value"
        }
    }
}

/** Parser for the `ZoneGroupState` document, which describes every room and how they are grouped. */
object Topology {

    fun parseGroups(state: String): List<SonosGroup> {
        val groups = mutableListOf<SonosGroup>()
        try {
            val parser = AndroidXml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(StringReader(state))
            }

            var groupId: String? = null
            var coordinatorUuid: String? = null
            var members = mutableListOf<SonosPlayer>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "ZoneGroup" -> {
                            groupId = parser.getAttributeValue(null, "ID")
                            coordinatorUuid = parser.getAttributeValue(null, "Coordinator")
                            members = mutableListOf()
                        }

                        "ZoneGroupMember" -> {
                            val isBridge = parser.getAttributeValue(null, "IsZoneBridge") == "1"
                            val uuid = parser.getAttributeValue(null, "UUID")
                            val name = parser.getAttributeValue(null, "ZoneName")
                            val host = hostOf(parser.getAttributeValue(null, "Location"))
                            if (!isBridge && uuid != null && name != null && host != null) {
                                members += SonosPlayer(uuid, name, host)
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> if (parser.name == "ZoneGroup") {
                        val coordinator = members.firstOrNull { it.uuid == coordinatorUuid }
                        if (coordinator != null) {
                            groups += SonosGroup(
                                id = groupId ?: coordinator.uuid,
                                coordinator = coordinator,
                                members = members.toList(),
                            )
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            return groups
        }
        return groups.sortedBy { it.coordinator.name.lowercase() }
    }

    private fun hostOf(location: String?): String? = location
        ?.substringAfter("://", "")
        ?.substringBefore('/')
        ?.substringBefore(':')
        .orNullIfBlank()
}
