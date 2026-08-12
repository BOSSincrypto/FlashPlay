package com.bossincrypto.flashplay

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import java.security.MessageDigest
import kotlin.math.roundToInt

class PlaybackPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var speed: Float
        get() = prefs.getFloat(KEY_SPEED, DEFAULT_SPEED)
        set(value) = prefs.edit { putFloat(KEY_SPEED, normalizeSpeed(value)) }

    fun positionFor(uri: Uri): Long = prefs.getLong(positionKey(uri), 0L).coerceAtLeast(0L)

    fun savePosition(uri: Uri?, positionMs: Long) {
        if (uri == null || positionMs < 0L) return
        prefs.edit { putLong(positionKey(uri), positionMs) }
    }

    fun saveLastUri(uri: Uri?) {
        val persistentUri = uri?.takeIf { it.scheme == "content" || it.scheme == "file" }
        prefs.edit { putString(KEY_LAST_URI, persistentUri?.toString()) }
    }

    fun lastUri(): Uri? = prefs.getString(KEY_LAST_URI, null)?.let(Uri::parse)

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
        const val STEP = 0.25f
        const val DEFAULT_SPEED = 1f
        private const val FILE_NAME = "playback"
        private const val KEY_SPEED = "speed"
        private const val KEY_LAST_URI = "last_uri"

        fun normalizeSpeed(value: Float): Float {
            val clamped = value.coerceIn(MIN_SPEED, MAX_SPEED)
            return ((clamped / STEP).roundToInt() * STEP).coerceIn(MIN_SPEED, MAX_SPEED)
        }

        private fun positionKey(uri: Uri): String = positionKey(uri.toString())

        private fun positionKey(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return "position_${digest.joinToString("") { byte -> "%02x".format(byte) }}"
        }

        internal fun positionKeyForTest(value: String): String = positionKey(value)
    }
}
