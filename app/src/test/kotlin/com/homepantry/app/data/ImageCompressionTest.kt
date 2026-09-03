package com.listacasa.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCompressionTest {
    @Test fun `images already within the max side are left unchanged`() {
        assertEquals(800 to 600, computeResizedDimensions(800, 600, maxSide = 1024))
    }

    @Test fun `landscape image is scaled down keeping aspect ratio`() {
        assertEquals(1024 to 576, computeResizedDimensions(2048, 1152, maxSide = 1024))
    }

    @Test fun `portrait image is scaled down keeping aspect ratio`() {
        assertEquals(576 to 1024, computeResizedDimensions(1152, 2048, maxSide = 1024))
    }

    @Test fun `square image exactly at the limit is unchanged`() {
        assertEquals(1024 to 1024, computeResizedDimensions(1024, 1024, maxSide = 1024))
    }
}
