package com.tony.nosotros.update

/** Candidate delivery is exclusive to the administrator; a missing approval never means latest. */
object UpdatePolicy {
    fun targetVersion(admin: Boolean, selectedTest: String?, approved: String?): String? =
        (if (admin) selectedTest else approved)?.takeIf { it.isNotBlank() }
}
