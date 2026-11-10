package com.plaintextlab.dumblauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9Test {
    private fun app(label: String) = AppEntry("x/y", label, null)

    @Test fun encodes() = assertEquals("2263", T9.encode("came"))

    @Test fun matchesWordStart() {
        assertTrue(app("Camera").matches("226"))
        assertTrue(app("Google Maps").matches("627"))   // "maps"
        assertTrue(app("Google Maps").matches("466"))   // "google"
        assertFalse(app("Camera").matches("227"))
    }

    @Test fun matchesJoinedWords() = assertTrue(app("You Tube").matches("9688823"))

    @Test fun emptyQueryMatchesAll() = assertTrue(app("Anything").matches(""))
}
