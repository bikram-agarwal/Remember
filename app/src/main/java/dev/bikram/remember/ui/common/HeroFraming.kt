package dev.bikram.remember.ui.common

import org.json.JSONObject

/**
 * User-controlled viewport into the single stored hero bitmap.
 * [focalX], [focalY] are normalized 0..1 in image space (point that sits at mask center).
 * [zoom] is >= 1, relative to the minimum uniform scale that covers the mask (cover scale).
 */
data class HeroFraming(
    val focalX: Float = 0.5f,
    val focalY: Float = 0.5f,
    val zoom: Float = 1f,
) {
    fun clamped(): HeroFraming =
        HeroFraming(
            focalX = focalX.coerceIn(0f, 1f),
            focalY = focalY.coerceIn(0f, 1f),
            zoom = zoom.coerceIn(1f, 8f),
        )

    fun toJsonString(): String =
        JSONObject()
            .put("v", 1)
            .put("fx", focalX.toDouble())
            .put("fy", focalY.toDouble())
            .put("z", zoom.toDouble())
            .toString()

    companion object {
        fun fromJsonString(raw: String?): HeroFraming? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                if (o.optInt("v", 1) != 1) return@runCatching null
                HeroFraming(
                    focalX = o.optDouble("fx", 0.5).toFloat(),
                    focalY = o.optDouble("fy", 0.5).toFloat(),
                    zoom = o.optDouble("z", 1.0).toFloat().coerceAtLeast(1f),
                ).clamped()
            }.getOrNull()
        }
    }
}
