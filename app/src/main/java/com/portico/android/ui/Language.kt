package com.portico.android.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language.
 *
 * Android has had a real per-app language since 13, and AppCompat backports it
 * to everything Portico supports. Both paths persist the choice themselves, so
 * there is no preference to keep in sync and no way for the stored value and
 * the actual language to disagree.
 *
 * Setting the locale recreates the activity, which is what makes every label
 * re-read; that is the platform behaving correctly, not a glitch to suppress.
 */
enum class AppLanguage(val tag: String, val label: String) {
    /** Follow whatever the device is set to. */
    SYSTEM("", "System"),
    ENGLISH("en", "English"),
    BANGLA("bn", "বাংলা");

    companion object {
        /** The language currently in force, read from the platform. */
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales()
                .toLanguageTags()
                .substringBefore(',')
                .substringBefore('-')
            return entries.firstOrNull { it.tag.isNotEmpty() && it.tag == tag } ?: SYSTEM
        }

        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                if (language.tag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(language.tag)
                }
            )
        }
    }
}
