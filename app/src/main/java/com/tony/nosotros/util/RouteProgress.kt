package com.tony.nosotros.util

import kotlin.math.cos

/** Projection onto the route; a GPS jump away from the path never advances a turn. */
object RouteProgress {
    data class Fix(val alongM: Double, val awayM: Double)
    fun locate(points: List<Pt>, position: Pt): Fix? {
        if(points.size < 2)return null
        var walked=0.0; var best: Fix?=null
        for(i in 0 until points.lastIndex){
            val a=points[i];val b=points[i+1]
            val scale=cos(Math.toRadians(position.lat)).coerceAtLeast(.01)
            val dx=(b.lng-a.lng)*scale;val dy=b.lat-a.lat
            val px=(position.lng-a.lng)*scale;val py=position.lat-a.lat
            val fraction=if(dx*dx+dy*dy == 0.0)0.0 else ((px*dx+py*dy)/(dx*dx+dy*dy)).coerceIn(0.0,1.0)
            val projected=Pt(a.lat+(b.lat-a.lat)*fraction,a.lng+(b.lng-a.lng)*fraction)
            val length=Geo.dist(a.lat,a.lng,b.lat,b.lng)
            val away=Geo.dist(position.lat,position.lng,projected.lat,projected.lng)
            if(best==null||away<best.awayM)best=Fix(walked+length*fraction,away)
            walked+=length
        }
        return best
    }
    fun nextStep(distances: List<Double>, alongM: Double): Int {
        var end=0.0
        for(i in distances.indices){end+=distances[i];if(alongM<end-8)return (i+1).coerceAtMost(distances.lastIndex)}
        return distances.lastIndex.coerceAtLeast(0)
    }
}
