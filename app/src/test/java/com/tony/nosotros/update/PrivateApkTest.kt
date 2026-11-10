package com.tony.nosotros.update

import org.junit.Assert.*
import org.junit.Test

class PrivateApkTest {
    private val hash = "a".repeat(64)
    @Test fun acceptsExactAndPartialLastChunks() {
        assertTrue(PrivateApk.validManifest(524288, 1, 524288, hash))
        assertTrue(PrivateApk.validManifest(524289, 2, 524288, hash))
    }
    @Test fun rejectsOversizedOrInconsistentDownloads() {
        assertFalse(PrivateApk.validManifest(0, 0, 524288, hash))
        assertFalse(PrivateApk.validManifest(100L*1024*1024+1, 201, 524288, hash))
        assertFalse(PrivateApk.validManifest(524289, 1, 524288, hash))
        assertFalse(PrivateApk.validManifest(20, 1, 1, hash))
        assertFalse(PrivateApk.validManifest(20, 1, 524288, "invalid"))
    }
}
