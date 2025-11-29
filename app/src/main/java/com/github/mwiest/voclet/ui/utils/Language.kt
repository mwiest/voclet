package com.github.mwiest.voclet.ui.utils

data class Language(val code: String, val nativeName: String, val flagEmoji: String)

val LANGUAGES = listOf(
    Language("en", "English", countryFlag("gb")),
    Language("de", "Deutsch", countryFlag("de")),
    Language("fr", "Français", countryFlag("fr")),
    Language("es", "Español", countryFlag("es"))
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