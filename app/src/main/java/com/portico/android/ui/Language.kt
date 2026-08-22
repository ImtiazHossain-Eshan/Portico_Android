package com.portico.android.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language.
 *
 * Android 13 gave every app a real per-app language, and AppCompat backports it
 * to older releases. Both paths persist the choice themselves, so there is no
 * preference to keep in sync and no way for a stored value and the actual
 * language to disagree.
 *
 * The framework is called directly on 33 and above rather than going through
 * AppCompat. AppCompat's setter routes through its delegate, and this app's
 * activity is a plain ComponentActivity, so that delegate never runs and the
 * call is silently dropped: the choice appears to be made and nothing changes.
 * Below 33 there is no framework API, so AppCompat's storage is the only
 * option, and the manifest service that backs it is declared for that reason.
 *
 * Setting a locale recreates the activity, which is what makes every label
 * re-read. That is the platform working, not a glitch to suppress.
 */
enum class AppLanguage(val tag: String, val label: String) {
    /** Follow whatever the device is set to. */
    SYSTEM("", "System"),
    ENGLISH("en", "English"),
    BANGLA("bn", "বাংলা");

    companion object {

        /** The language currently in force, read back from wherever it was set. */
        fun current(context: Context): AppLanguage {
            val tags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java)
                    ?.applicationLocales
                    ?.toLanguageTags()
                    .orEmpty()
            } else {
                AppCompatDelegate.getApplicationLocales().toLanguageTags()
            }

            val tag = tags.substringBefore(',').substringBefore('-')
            return entries.firstOrNull { it.tag.isNotEmpty() && it.tag == tag } ?: SYSTEM
        }

        fun apply(context: Context, language: AppLanguage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val manager = context.getSystemService(LocaleManager::class.java) ?: return
                manager.applicationLocales =
                    if (language.tag.isEmpty()) LocaleList.getEmptyLocaleList()
                    else LocaleList.forLanguageTags(language.tag)
            } else {
                AppCompatDelegate.setApplicationLocales(
                    if (language.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(language.tag)
                )
            }
        }
    }
}
