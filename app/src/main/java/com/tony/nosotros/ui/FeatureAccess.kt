package com.tony.nosotros.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.tony.nosotros.data.FeatureAccess

val LocalFeatures = staticCompositionLocalOf { FeatureAccess() }
