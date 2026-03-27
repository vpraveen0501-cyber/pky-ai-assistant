package com.pkyai.android.ui.theme

import androidx.compose.ui.graphics.Color

// PKY AI Assistant Core Colors (Atmospheric Intelligence Palette)
val PkyAiPrimary = Color(0xFF97A9FF)
val PkyAiPrimaryDim = Color(0xFF3E65FF)
val PkyAiSecondary = Color(0xFFBF81FF)
val PkyAiSecondaryDim = Color(0xFF9C42F4)
val PkyAiSecondaryContainer = Color(0xFF7701D0)
val PkyAiTertiary = Color(0xFF99F7FF)
val PkyAiTertiaryFixed = Color(0xFF00F1FE)

// Brain-Specific Accents
val BrainGeneral = Color(0xFF97A9FF)
val BrainBuild = Color(0xFF00D2FF)
val BrainPlan = Color(0xFFFF8A00)
val BrainAdaptive = Color(0xFF00FF94)
val BrainExtended = Color(0xFFFF00B8)

// Tonal Surfaces & Background
val SurfaceDim = Color(0xFF0E0E12)
val SurfaceContainerLow = Color(0xFF131317)
val SurfaceContainer = Color(0xFF19191E)
val SurfaceContainerHigh = Color(0xFF1F1F25)
val SurfaceContainerHighest = Color(0xFF25252B)
val SurfaceBright = Color(0xFF2C2B32)

// Legacy Aliases for backwards compatibility during migration
val PkyAiBlue = PkyAiPrimary
val PkyAiBlueDark = PkyAiPrimaryDim
val PkyAiRed = Color(0xFFFF716C) // Soft red/error
val PkyAiGold = Color(0xFFFFD700) 

// Glassmorphism System
val GlassBackground = SurfaceContainer.copy(alpha = 0.6f) 
val GlassStroke = Color(0xFF48474C).copy(alpha = 0.3f)
val GlassSurface = SurfaceContainerHighest.copy(alpha = 0.4f)

// Text Colors
val TextPrimary = Color(0xFFF3EFF6) // on-surface
val TextSecondary = Color(0xFFACAAB0) // on-surface-variant
val AppBackground = SurfaceDim

// Midnight Galaxy Palette (from theme-factory)
val GalaxyDeepPurple = Color(0xFF2B1E3E)
val GalaxyCosmicBlue = Color(0xFF4A4E8F)
val GalaxyLavender = Color(0xFFA490C2)
val GalaxySilver = Color(0xFFE6E6FA)

// Ocean Depths Palette (from theme-factory)
val OceanDeepNavy = Color(0xFF1A2332)
val OceanTeal = Color(0xFF2D8B8B)
val OceanSeafoam = Color(0xFFA8DADC)
val OceanCream = Color(0xFFF1FAEE)

// UI Element Colors
val CardBackground = SurfaceContainer

