package com.tony.nosotros.update
import org.junit.Assert.*
import org.junit.Test
class ForcedUpdateTest {
    private fun allowed(admin: Boolean=false,version: String="1.11.0",forced: String?="1.11.0",nonce:Long=2,handled:Long=1,
        wifi:Boolean=true,mobile:Boolean=false,pref:Boolean=false,feature:Boolean=false)=UpdatePolicy.automaticAllowed(admin,version,forced,nonce,handled,pref,feature,wifi,mobile)
    @Test fun approvedOrderOverridesAutomaticPreferenceOnce() { assertTrue(allowed());assertFalse(allowed(handled=2)) }
    @Test fun ownerTrialsAndDifferentVersionsAreNeverForced() { assertFalse(allowed(admin=true));assertFalse(allowed(forced="1.10.0")) }
    @Test fun meteredRequiresExplicitOwnerChoice() { assertFalse(allowed(wifi=false));assertTrue(allowed(wifi=false,mobile=true)) }
    @Test fun normalAutomaticUpdatesKeepBothPreferencesAndWifi() { assertTrue(allowed(forced=null,pref=true,feature=true));assertFalse(allowed(forced=null,pref=true)) }
}
