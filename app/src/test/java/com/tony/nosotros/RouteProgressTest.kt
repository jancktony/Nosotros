package com.tony.nosotros
import com.tony.nosotros.util.*
import org.junit.Assert.*
import org.junit.Test
class RouteProgressTest {
    private val route=listOf(Pt(10.0, -74.0), Pt(10.01,-74.0), Pt(10.01,-73.99))
    @Test fun projectsAlongTheCurrentLeg(){val fix=RouteProgress.locate(route,Pt(10.005,-74.0))!!;assertTrue(fix.awayM<1);assertTrue(fix.alongM in 550.0..562.0)}
    @Test fun detectsLeavingRouteInsteadOfAdvancingBlindly(){val fix=RouteProgress.locate(route,Pt(10.005,-74.01))!!;assertTrue(fix.awayM>900)}
    @Test fun handlesEmptyRoutesAndDuplicatePoints(){assertNull(RouteProgress.locate(emptyList(),Pt(0.0,0.0)));assertEquals(0.0,RouteProgress.locate(listOf(route[0],route[0]),route[0])!!.alongM,0.01)}
    @Test fun selectsNextTurnAndCapsAtArrival(){assertEquals(1,RouteProgress.nextStep(listOf(100.0,200.0,0.0),10.0));assertEquals(2,RouteProgress.nextStep(listOf(100.0,200.0,0.0),105.0));assertEquals(2,RouteProgress.nextStep(listOf(100.0,200.0,0.0),999.0))}
}
