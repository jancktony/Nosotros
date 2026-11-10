package com.tony.nosotros.data

import org.junit.Assert.*
import org.junit.Test

class FeaturesTest {
    @Test fun newVoiceModesStartDisabledForEveryone() {
        for (admin in listOf(false, true)) for (feature in listOf(Feature.WALKIE, Feature.VOICE_NOTES))
            assertFalse(FeatureAccess(admin = admin).enabled(feature))
    }
    @Test fun privatePreviewFollowsAdministratorRole() {
        val flags = mapOf("walkie" to "admin")
        assertTrue(FeatureAccess(flags, true).enabled(Feature.WALKIE))
        assertFalse(FeatureAccess(flags, false).enabled(Feature.WALKIE))
    }
    @Test fun rolloutAndWithdrawalApplyToBothRoles() {
        for (feature in Feature.entries) for (admin in listOf(false, true)) {
            assertTrue(FeatureAccess(mapOf(feature.id to "all"), admin).enabled(feature))
            assertFalse(FeatureAccess(mapOf(feature.id to "off"), admin).enabled(feature))
            assertFalse(FeatureAccess(mapOf(feature.id to "invalid"), admin).enabled(feature))
        }
    }
    @Test fun establishedFeaturesKeepTheirDefaults() {
        assertTrue(FeatureAccess().enabled(Feature.MAP_3D))
        assertTrue(FeatureAccess().enabled(Feature.PROFILE_PHOTO))
    }
}
