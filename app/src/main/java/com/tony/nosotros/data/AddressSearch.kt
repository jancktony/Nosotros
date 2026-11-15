package com.tony.nosotros.data

import com.tony.nosotros.util.Geo
import com.tony.nosotros.util.Pt

/** Postal address candidates. Apartment/tower data is not sent to public geocoders. */
object AddressSearch {
    data class Candidate(val text: String, val approximate: Boolean = false)
    fun candidates(input: String, near: Pt?): List<Candidate> {
        val raw=input.trim().replace(Regex("\\s+")," ").take(300)
        if(raw.isBlank())return emptyList()
        val explicitCity=Regex("(?i)\\b(Barranquilla|Riohacha)\\b").find(raw)?.value
        val city=explicitCity ?: near?.let { p ->
            listOf("Barranquilla" to Pt(10.99,-74.80),"Riohacha" to Pt(11.54,-72.91))
                .minByOrNull { Geo.dist(p.lat,p.lng,it.second.lat,it.second.lng) }
                ?.takeIf { Geo.dist(p.lat,p.lng,it.second.lat,it.second.lng)<30_000 }?.first
        }
        var clean=raw.replace(Regex("(?i)\\b(?:torre|apartamento|apto\\.?|apartment|piso|interior|int\\.)\\s*\\d.*$"),"")
            .replace(Regex("(?i)\\b(?:al lado de|al lado|cerca de|frente a)\\b.*$"),"").trim(' ', ',')
            .replace(Regex("(?i)\\b(?:numero|número|nro\\.?)\\b"),"#")
        clean=clean.replace(Regex("(?i)^(?:cl\\.?|cll\\.?)\\s*"),"Calle ")
            .replace(Regex("(?i)^(?:cra\\.?|cr\\.?|kr\\.?)\\s*"),"Carrera ")
        val street=Regex("(?i)^(calle|carrera|avenida|diagonal|transversal)\\s*(\\d+[a-z]?(?:\\s+bis)?(?:\\s+(?:sur|norte|este|oeste))?)\\b").find(clean)
        fun located(text: String): String = if(city!=null&&!text.contains(city,true)) "$text, $city, Colombia" else text
        if(street==null)return listOf(Candidate(clean))
        val road=street.value.trim()
        val rest=clean.substring(street.range.last+1).trim()
        val number=Regex("^(?:#\\s*)?(\\d+[a-z]?)\\s*[-–]\\s*(\\d+[a-z]?)",RegexOption.IGNORE_CASE).find(rest)
        val building=Regex("(?i)\\b(?:conjunto|condominio|urbanizacion|urbanización)\\s+(.+?)(?=,|$)").find(clean)?.value
        return buildList {
            if(number!=null)add(Candidate(located("$road # ${number.groupValues[1]}-${number.groupValues[2]}")))
            else add(Candidate(located(clean)))
            if(building!=null)add(Candidate(located(building),true))
            add(Candidate(located(road),true))
        }.distinctBy { it.text.lowercase() }
    }
}
