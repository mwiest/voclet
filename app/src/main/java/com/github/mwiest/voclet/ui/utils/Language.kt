package com.github.mwiest.voclet.ui.utils

data class LanguageVariant(val code: String, val displayName: String)

data class Language(
    val code: String,
    val nativeName: String,
    val flagEmoji: String,
    val commonVariants: List<LanguageVariant> = emptyList()
)

val LANGUAGES = listOf(
    Language("en", "English", countryFlag("gb"), listOf(
        LanguageVariant("en-US", "English (US)"),
        LanguageVariant("en-GB", "English (UK)"),
        LanguageVariant("en-AU", "English (AU)"),
        LanguageVariant("en-IN", "English (IN)"),
    )),
    Language("de", "Deutsch", countryFlag("de"), listOf(
        LanguageVariant("de-DE", "Deutsch (Deutschland)"),
        LanguageVariant("de-AT", "Deutsch (Österreich)"),
        LanguageVariant("de-CH", "Deutsch (Schweiz)"),
    )),
    Language("fr", "Français", countryFlag("fr"), listOf(
        LanguageVariant("fr-FR", "Français (France)"),
        LanguageVariant("fr-CA", "Français (Canada)"),
        LanguageVariant("fr-BE", "Français (Belgique)"),
    )),
    Language("es", "Español", countryFlag("es"), listOf(
        LanguageVariant("es-ES", "Español (España)"),
        LanguageVariant("es-MX", "Español (México)"),
        LanguageVariant("es-AR", "Español (Argentina)"),
        LanguageVariant("es-CO", "Español (Colombia)"),
    )),
)

fun String.isoToLanguage(): Language? {
    return LANGUAGES.find { it.code == this }
}

private fun countryFlag(code: String) = code
    .uppercase()
    .split("")
    .filter { it.isNotBlank() }
    .map { it.codePointAt(0) + 0x1F1A5 }
    .joinToString("") { String(Character.toChars(it)) }