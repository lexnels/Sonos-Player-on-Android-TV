package com.sonostv.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.min

const val ArtSampleSize = 32

data class ArtPalette(
    val topLeft: Color,
    val topRight: Color,
    val bottomLeft: Color,
    val bottomRight: Color,
    val base: Color,
) {
    companion object {
        val Default = ArtPalette(
            topLeft = Color(0xFF1A1A20),
            topRight = Color(0xFF141419),
            bottomLeft = Color(0xFF121217),
            bottomRight = Color(0xFF0C0C10),
            base = Color(0xFF0A0A0D),
        )

        private const val MinHueSeparation = 42f
        private const val AccentCount = 4

        fun from(bitmap: Bitmap): ArtPalette {
            val small = runCatching {
                Bitmap.createScaledBitmap(bitmap, ArtSampleSize, ArtSampleSize, true)
            }.getOrNull() ?: return Default

            val half = ArtSampleSize / 2
            val localDominants = listOf(
                small.findDominantHsv(0, 0, half, half),
                small.findDominantHsv(half, 0, ArtSampleSize, half),
                small.findDominantHsv(0, half, half, ArtSampleSize),
                small.findDominantHsv(half, half, ArtSampleSize, ArtSampleSize),
            )

            val candidates = small.collectVividCandidates()
            val global = candidates.maxByOrNull { it.score }?.hsv ?: localDominants.first()
            val dominant = small.findOverallDominantHsv()
            val distinct = selectDistinctColors(candidates, global)
            val assigned = assignToQuadrants(localDominants, distinct)

            return ArtPalette(
                topLeft = assigned[0].toAccentColor(),
                topRight = assigned[1].toAccentColor(),
                bottomLeft = assigned[2].toAccentColor(),
                bottomRight = assigned[3].toAccentColor(),
                base = dominant.toBackgroundTone(),
            )
        }

        private data class VividCandidate(val hsv: FloatArray, val score: Float)

        private fun Bitmap.collectVividCandidates(): List<VividCandidate> =
            buildList {
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        val hsv = getPixel(x, y).toHsv()
                        val score = vividnessScore(hsv)
                        if (score > 0f) add(VividCandidate(hsv, score))
                    }
                }
            }.sortedByDescending { it.score }

        /**
         * Pick [AccentCount] vivid colours from the artwork. Prefer hue variety when
         * the cover actually has it, but never invent colours that aren't in the art.
         */
        private fun selectDistinctColors(
            candidates: List<VividCandidate>,
            fallback: FloatArray,
        ): List<FloatArray> {
            if (candidates.isEmpty()) {
                return List(AccentCount) { fallback.copyOf() }
            }

            val selected = mutableListOf<FloatArray>()

            // Phase 1 — spread hues only when the art genuinely offers them.
            for (candidate in candidates) {
                if (selected.size >= AccentCount) break
                if (selected.any { nearHue(candidate.hsv, it) || sameColor(candidate.hsv, it) }) continue
                selected.add(candidate.hsv.copyOf())
            }

            // Phase 2 — similar hues are fine; take the next-best real pixels.
            for (candidate in candidates) {
                if (selected.size >= AccentCount) break
                if (selected.any { sameColor(candidate.hsv, it) }) continue
                selected.add(candidate.hsv.copyOf())
            }

            // Phase 3 — same hue family, nudged lighter/darker (still from the source).
            var shade = 0.88f
            while (selected.size < AccentCount) {
                val variant = selected.first().copyOf()
                variant[2] = (variant[2] * shade).coerceIn(0.28f, 0.82f)
                shade *= 0.82f
                if (selected.any { sameColor(variant, it) }) break
                selected.add(variant)
            }

            while (selected.size < AccentCount) {
                selected.add(selected.first().copyOf())
            }

            return selected
        }

        /** Map each quadrant's local winner to the closest unused distinct colour. */
        private fun assignToQuadrants(
            localDominants: List<FloatArray>,
            distinct: List<FloatArray>,
        ): List<FloatArray> {
            val available = distinct.indices.toMutableList()
            val assigned = Array(AccentCount) { distinct[0].copyOf() }

            for (quadrant in localDominants.indices.sortedByDescending { vividnessScore(localDominants[it]) }) {
                val pick = available.minByOrNull { hueDistance(localDominants[quadrant], distinct[it]) }
                    ?: available.first()
                assigned[quadrant] = distinct[pick].copyOf()
                available.remove(pick)
            }

            return assigned.toList()
        }

        /**
         * Area-weighted dominant hue across the whole cover — what you'd perceive as
         * the "main" colour, not the brightest accent pixel.
         */
        private fun Bitmap.findOverallDominantHsv(): FloatArray {
            var sinSum = 0.0
            var cosSum = 0.0
            var satSum = 0f
            var valSum = 0f
            var weightSum = 0f

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val hsv = getPixel(x, y).toHsv()
                    val weight = dominantWeight(hsv)
                    if (weight <= 0f) continue
                    val rad = Math.toRadians(hsv[0].toDouble())
                    sinSum += sin(rad) * weight
                    cosSum += cos(rad) * weight
                    satSum += hsv[1] * weight
                    valSum += hsv[2] * weight
                    weightSum += weight
                }
            }

            if (weightSum <= 0f) return floatArrayOf(0f, 0.35f, 0.30f)

            var hue = Math.toDegrees(atan2(sinSum, cosSum)).toFloat()
            if (hue < 0f) hue += 360f
            return floatArrayOf(hue, satSum / weightSum, valSum / weightSum)
        }

        /** Every pixel counts, but colourful mid-tones outweigh glare, shadow, and grey. */
        private fun dominantWeight(hsv: FloatArray): Float {
            val saturation = hsv[1]
            val value = hsv[2]
            if (value < 0.05f) return 0f
            if (value > 0.97f && saturation < 0.12f) return 0f
            return (0.25f + saturation * 0.75f) * value.coerceIn(0.10f, 1f)
        }

        private fun Bitmap.findDominantHsv(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            fallback: FloatArray = floatArrayOf(0f, 0.35f, 0.30f),
        ): FloatArray {
            var best = fallback.copyOf()
            var bestScore = -1f

            for (x in left until right) {
                for (y in top until bottom) {
                    val hsv = getPixel(x, y).toHsv()
                    val score = vividnessScore(hsv)
                    if (score > bestScore) {
                        bestScore = score
                        best = hsv
                    }
                }
            }

            return best
        }

        private fun hueDistance(a: FloatArray, b: FloatArray): Float {
            val diff = abs(a[0] - b[0])
            return min(diff, 360f - diff)
        }

        private fun nearHue(a: FloatArray, b: FloatArray): Boolean =
            hueDistance(a, b) < MinHueSeparation

        private fun sameColor(a: FloatArray, b: FloatArray): Boolean =
            hueDistance(a, b) < 10f &&
                abs(a[1] - b[1]) < 0.12f &&
                abs(a[2] - b[2]) < 0.10f

        private fun FloatArray.toBackgroundTone(): Color {
            val hsv = copyOf()
            hsv[2] = (hsv[2] * 0.48f).coerceIn(0.34f, 0.50f)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }

        private fun FloatArray.toAccentColor(): Color {
            val hsv = copyOf()
            hsv[2] = (hsv[2] * 0.88f).coerceIn(0.52f, 0.78f)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }

        /** Prefer fully saturated brights; only skip true whites and deep shadows. */
        private fun vividnessScore(hsv: FloatArray): Float {
            val saturation = hsv[1]
            val value = hsv[2]
            if (value < 0.08f) return -1f
            if (value > 0.96f && saturation < 0.18f) return -1f
            if (saturation < 0.10f && value < 0.90f) return -1f
            return saturation * saturation * value
        }

        private fun Int.toHsv(): FloatArray {
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(
                (this shr 16) and 0xFF,
                (this shr 8) and 0xFF,
                this and 0xFF,
                hsv,
            )
            return hsv
        }
    }
}

@Composable
fun rememberArtPalette(artUrl: String?, roomKey: Any? = null): ArtPalette {
    val context = LocalContext.current
    var palette by remember { mutableStateOf(ArtPalette.Default) }

    LaunchedEffect(roomKey) {
        palette = ArtPalette.Default
    }

    LaunchedEffect(roomKey, artUrl) {
        palette = if (artUrl == null) {
            ArtPalette.Default
        } else {
            val request = ImageRequest.Builder(context)
                .data(artUrl)
                .size(ArtSampleSize)
                .allowHardware(false)
                .build()
            val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            bitmap?.let(ArtPalette::from) ?: ArtPalette.Default
        }
    }

    return palette
}
