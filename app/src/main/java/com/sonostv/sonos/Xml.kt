package com.sonostv.sonos

/** Minimal helpers for the small, flat XML documents that UPnP/SOAP responses use. */
object Xml {

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    fun tag(xml: String, name: String): String? {
        val regex = cache.getOrPut(name) {
            Regex("<(?:\\w+:)?$name(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$name>", RegexOption.DOT_MATCHES_ALL)
        }
        return regex.find(xml)?.groupValues?.get(1)
    }

    fun text(xml: String, name: String): String? = tag(xml, name)?.let { unescape(it).trim() }.orNullIfBlank()

    fun int(xml: String, name: String): Int? = text(xml, name)?.toIntOrNull()

    fun unescape(value: String): String {
        if ('&' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '&') {
                out.append(c)
                i++
                continue
            }
            val end = value.indexOf(';', i)
            if (end == -1 || end - i > 10) {
                out.append(c)
                i++
                continue
            }
            when (val entity = value.substring(i + 1, end)) {
                "lt" -> out.append('<')
                "gt" -> out.append('>')
                "amp" -> out.append('&')
                "quot" -> out.append('"')
                "apos" -> out.append('\'')
                else -> {
                    val code = when {
                        entity.startsWith("#x") || entity.startsWith("#X") ->
                            entity.drop(2).toIntOrNull(16)
                        entity.startsWith("#") -> entity.drop(1).toIntOrNull()
                        else -> null
                    }
                    if (code != null) out.appendCodePoint(code) else out.append('&').append(entity).append(';')
                }
            }
            i = end + 1
        }
        return out.toString()
    }

    fun escape(value: String): String = buildString(value.length) {
        for (c in value) when (c) {
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}

fun String?.orNullIfBlank(): String? = if (this.isNullOrBlank()) null else this

/** Parses UPnP durations of the form `H:MM:SS` (or `H:MM:SS.mmm`) into milliseconds. */
fun parseUpnpDuration(value: String?): Long {
    if (value.isNullOrBlank() || value == "NOT_IMPLEMENTED") return 0L
    val parts = value.trim().split(':')
    if (parts.size != 3) return 0L
    val hours = parts[0].toLongOrNull() ?: return 0L
    val minutes = parts[1].toLongOrNull() ?: return 0L
    val seconds = parts[2].substringBefore('.').toLongOrNull() ?: return 0L
    return ((hours * 3600) + (minutes * 60) + seconds) * 1000L
}

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
