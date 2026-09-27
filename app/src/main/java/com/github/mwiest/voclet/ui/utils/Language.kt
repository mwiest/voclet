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
    Language("pt", "Português", countryFlag("pt"), listOf(
        LanguageVariant("pt-PT", "Português (Portugal)"),
        LanguageVariant("pt-BR", "Português (Brasil)"),
    )),
    Language("it", "Italiano", countryFlag("it")),
    Language("nl", "Nederlands", countryFlag("nl"), listOf(
        LanguageVariant("nl-NL", "Nederlands (Nederland)"),
        LanguageVariant("nl-BE", "Nederlands (België)"),
    )),
    Language("pl", "Polski", countryFlag("pl")),
    Language("sv", "Svenska", countryFlag("se")),
    Language("nb", "Norsk bokmål", countryFlag("no")),
    Language("da", "Dansk", countryFlag("dk")),
    Language("fi", "Suomi", countryFlag("fi")),
    Language("hu", "Magyar", countryFlag("hu")),
)

/** Also accepts a region tag (`pt-BR`) and the macrolanguage `no`, which AI models tend to return. */
fun String.isoToLanguage(): Language? {
    val base = lowercase().substringBefore('-').substringBefore('_')
    val code = if (base == "no") "nb" else base
    return LANGUAGES.find { it.code == code }
}

private fun countryFlag(code: String) = code
    .uppercase()
    .split("")
    .filter { it.isNotBlank() }
    .map { it.codePointAt(0) + 0x1F1A5 }
    .joinToString("") { String(Character.toChars(it)) }