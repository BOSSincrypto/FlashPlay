package com.bossincrypto.flashplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaybackPrefsTest {
    @Test
    fun speedIsClampedAndSnappedToQuarterSteps() {
        assertEquals(0.25f, PlaybackPrefs.normalizeSpeed(0.01f))
        assertEquals(1.25f, PlaybackPrefs.normalizeSpeed(1.18f))
        assertEquals(4f, PlaybackPrefs.normalizeSpeed(9f))
    }

    @Test
    fun positionKeysDoNotAliasKnownJavaHashCollision() {
        assertNotEquals(
            PlaybackPrefs.positionKeyForTest("content://x/Aa"),
            PlaybackPrefs.positionKeyForTest("content://x/BB"),
        )
    }
}
