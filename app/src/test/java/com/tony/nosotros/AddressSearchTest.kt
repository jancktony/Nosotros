package com.tony.nosotros
import com.tony.nosotros.data.AddressSearch
import com.tony.nosotros.util.Pt
import org.junit.Assert.*
import org.junit.Test
class AddressSearchTest {
    @Test fun postalDetailsDoNotPreventSearchingTheStreet(){
        val hits=AddressSearch.candidates("calle 100 número 23-45 conjunto Jardines torre 8 apto 205 al lado del parque",Pt(10.99,-74.80))
        assertEquals("calle 100 # 23-45, Barranquilla, Colombia",hits[0].text)
        assertTrue(hits.any{it.text.contains("conjunto Jardines",true)})
        assertTrue(hits.last().approximate)
        assertTrue(hits.none{it.text.contains("205")||it.text.contains("torre",true)||it.text.contains("al lado",true)})
    }
    @Test fun explicitCityWinsOverCurrentPosition(){val hits=AddressSearch.candidates("Calle 10 # 5-20, Riohacha",Pt(10.99,-74.80));assertTrue(hits.first().text.contains("Riohacha"));assertFalse(hits.first().text.contains("Barranquilla"))}
    @Test fun citySearchIsNotRewrittenIntoAStreet(){assertEquals("Barranquilla",AddressSearch.candidates("Barranquilla",Pt(11.54,-72.91)).single().text)}
    @Test fun handlesCommonStreetAbbreviations(){assertTrue(AddressSearch.candidates("Cra. 23 # 10-15",Pt(11.54,-72.91)).first().text.startsWith("Carrera 23 # 10-15"))}
    @Test fun emptyQueryDoesNotCreateNetworkCandidates(){assertTrue(AddressSearch.candidates("  ",null).isEmpty())}
}
