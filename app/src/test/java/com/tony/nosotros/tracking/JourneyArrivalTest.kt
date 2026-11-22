package com.tony.nosotros.tracking
import org.junit.Assert.*
import org.junit.Test
class JourneyArrivalTest {
 @Test fun etaAndSingleJumpNeverConfirmArrival(){val a=JourneyArrival();assertFalse(a.fix(1000,20.0,10f));assertFalse(a.fix(19000,20.0,10f));assertTrue(a.fix(21000,20.0,10f))}
 @Test fun movingAwayOrPoorAccuracyResetsDwell(){val a=JourneyArrival();a.fix(1000,20.0,10f);assertFalse(a.fix(20000,60.0,10f));assertFalse(a.fix(21000,20.0,10f));assertFalse(a.fix(45000,20.0,100f));assertFalse(a.fix(46000,20.0,10f))}
 @Test fun cancellationAndNewDestinationDiscardOldDwell(){val a=JourneyArrival();a.fix(1000,20.0,10f);a.reset();assertFalse(a.fix(50000,20.0,10f));assertFalse(a.fix(51000,Double.NaN,10f))}
}
