package com.tony.nosotros.update

/** Candidate delivery is exclusive to the administrator; a missing approval never means latest. */
object UpdatePolicy {
    fun targetVersion(admin: Boolean, selectedTest: String?, approved: String?): String? =
        (if (admin) selectedTest else approved)?.takeIf { it.isNotBlank() }
    fun automaticAllowed(admin: Boolean, version: String, forceVersion: String?, nonce: Long, handled: Long,
                         preference: Boolean, feature: Boolean, wifi: Boolean, allowMetered: Boolean): Boolean {
        val forced = !admin && forceVersion == version && nonce > 0 && nonce > handled
        return (forced || preference && feature) && (wifi || forced && allowMetered)
    }
}
