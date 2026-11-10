package com.tony.nosotros.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun ownerReceivesOnlySelectedTest() { assertEquals("2.0", UpdatePolicy.targetVersion(true, "2.0", "1.8")) }
    @Test fun partnerNeverReceivesUnapprovedCandidate() { assertEquals("1.8", UpdatePolicy.targetVersion(false, "2.0", "1.8")) }
    @Test fun ownerDoesNotInstallUnselectedCandidate() { assertNull(UpdatePolicy.targetVersion(true, null, "1.8")) }
    @Test fun missingApprovalNeverEnablesAutomaticLatest() { assertNull(UpdatePolicy.targetVersion(false, "2.0", null)) }
    @Test fun blankApprovalDoesNotEnableLatest() { assertNull(UpdatePolicy.targetVersion(false, "2.0", "")) }
    @Test fun approvalIsIndependentOfNewestCandidate() { assertEquals("1.9", UpdatePolicy.targetVersion(false, "2.0", "1.9")) }
}
