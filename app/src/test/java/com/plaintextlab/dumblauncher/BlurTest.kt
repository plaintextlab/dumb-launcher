package com.plaintextlab.dumblauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test fun uniformImageStaysTheSame() {
        val px = IntArray(25) { 0xFF336699.toInt() }
        Blur.boxBlur(px, 5, 5, 2)
        assertTrue(px.all { it == 0xFF336699.toInt() })
    }

    @Test fun brightSpotSpreadsToNeighbours() {
        val px = IntArray(25) { black }.also { it[12] = white } // centre of 5x5
        Blur.boxBlur(px, 5, 5, 1)
        val centre = px[12] and 255
        val neighbour = px[11] and 255
        val corner = px[0] and 255
        assertEquals(255 / 9, centre)          // 3x3 average of one white pixel
        assertEquals(centre, neighbour)        // spread evenly around it
        assertEquals(0, corner)                // nothing reaches 2 pixels away with r=1
        assertEquals(255, px[12] ushr 24)      // stays opaque
    }
}
