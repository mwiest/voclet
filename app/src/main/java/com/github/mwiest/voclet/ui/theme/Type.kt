package com.github.mwiest.voclet.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.github.mwiest.voclet.R

@OptIn(ExperimentalTextApi::class)
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// 2. Define the font name you want to use from Google Fonts

@OptIn(ExperimentalTextApi::class)
val fontNameBase = GoogleFont("Red Hat Text")

@OptIn(ExperimentalTextApi::class)
val BaseFontFamily = FontFamily(
    Font(googleFont = fontNameBase, fontProvider = provider),
    Font(googleFont = fontNameBase, fontProvider = provider, weight = FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
val fontName = GoogleFont("Finger Paint")

@OptIn(ExperimentalTextApi::class)
val AppBrandFontFamily = FontFamily(
    Font(googleFont = fontName, fontProvider = provider),
    Font(googleFont = fontName, fontProvider = provider, weight = FontWeight.Bold),
)

// Set of Material typography styles to start with
private val defaultTypography = Typography()
val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = AppBrandFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = AppBrandFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = AppBrandFontFamily),

    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = AppBrandFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = AppBrandFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = AppBrandFontFamily),

    titleLarge = defaultTypography.titleLarge.copy(fontFamily = BaseFontFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = BaseFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = BaseFontFamily),

    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = BaseFontFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = BaseFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = BaseFontFamily),

    labelLarge = defaultTypography.labelLarge.copy(fontFamily = BaseFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = BaseFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = BaseFontFamily)
)