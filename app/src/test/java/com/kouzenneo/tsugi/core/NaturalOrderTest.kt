package com.kouzenneo.tsugi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderTest {

    @Test
    fun `numbers compare by value not by character`() {
        val sorted = listOf("Screenshot_10.png", "Screenshot_2.png", "Screenshot_1.png")
            .sortedWith(NaturalOrder)

        assertEquals(
            listOf("Screenshot_1.png", "Screenshot_2.png", "Screenshot_10.png"),
            sorted,
        )
    }

    @Test
    fun `leading zeros do not change the order`() {
        val sorted = listOf("IMG_007.jpg", "IMG_08.jpg", "IMG_8.jpg").sortedWith(NaturalOrder)

        assertEquals(listOf("IMG_007.jpg", "IMG_08.jpg", "IMG_8.jpg"), sorted)
    }

    @Test
    fun `case is ignored for plain text runs`() {
        val sorted = listOf("beta.png", "Alpha.png", "gamma.png").sortedWith(NaturalOrder)

        assertEquals(listOf("Alpha.png", "beta.png", "gamma.png"), sorted)
    }

    @Test
    fun `prefix is ordered before longer names`() {
        val sorted = listOf("shot_1_extra.png", "shot_1.png").sortedWith(NaturalOrder)

        assertEquals(listOf("shot_1.png", "shot_1_extra.png"), sorted)
    }
}
