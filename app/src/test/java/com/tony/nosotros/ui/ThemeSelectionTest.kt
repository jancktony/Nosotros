package com.tony.nosotros.ui
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
class ThemeSelectionTest {
 @Test fun originalPinkRemainsTheFallback(){assertEquals(Palettes.Pink,Palettes.of(null));assertEquals(Palettes.Pink,Palettes.of("invalid"));assertEquals(Color(0xFFF9F0F3),Palettes.Pink.bg)}
 @Test fun everyChoiceRestoresItsFullPalette(){for((_,p) in Palettes.choices)assertEquals(p,Palettes.of(p.id));assertEquals(6,Palettes.choices.map{it.second.id}.distinct().size)}
 @Test fun redBlackIsSaturatedRedAndBlack(){assertEquals(Color(0xFFE6002E),Palettes.RedBlack.me);assertEquals(Color(0xFF0B0B0D),Palettes.RedBlack.bg);assertTrue(Palettes.RedBlack.dark)}
 @Test fun pinkVariantsHaveDifferentSurfaces(){assertTrue(Palettes.BlackPink.dark);assertFalse(Palettes.FullPink.dark);assertNotEquals(Palettes.Pink.surface,Palettes.FullPink.surface)}
}
