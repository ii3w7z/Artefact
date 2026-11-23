package com.chethan616.clearpdf.ui.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.chethan616.clearpdf.R
import java.util.Locale

/** App language selection, with Android's per-app language setting on 13+ and a context locale earlier. */
object AppLocale {
    private const val SIMPLIFIED_CHINESE = "zh-CN"
    private val simplifiedChineseRegions = setOf("", "CN", "SG", "MY")
    val supportedTags = listOf("en", "ar", "de", "es", "fr", "it", "pt", "pt-BR", "ru", SIMPLIFIED_CHINESE)

    private const val APP_PREFS = "app"
    private const val APP_KEY = "appLanguage"
    private const val LEGACY_PREFS = "clearpdf_onboarding"
    private const val LEGACY_KEY = "selected_locale"

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = selectedTag(base)
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    fun selectedTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val systemChoice = context.getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales.toLanguageTags()
            if (systemChoice.isNotBlank()) return normalize(systemChoice.substringBefore(','))
        }
        val prefs = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(APP_KEY)) return normalize(prefs.getString(APP_KEY, "").orEmpty())
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        return if (legacy.contains(LEGACY_KEY)) normalize(legacy.getString(LEGACY_KEY, "").orEmpty()) else ""
    }

    fun select(context: Context, tag: String) {
        val normalized = normalize(tag)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(normalized)
        } else {
            context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit().putString(APP_KEY, normalized).apply()
            (context as? Activity)?.recreate()
        }
    }

    /** Preserve the user's language from ClearPDF when moving to the Android system language API. */
    fun migrateLegacyLocale(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val manager = context.getSystemService(android.app.LocaleManager::class.java)
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (manager.applicationLocales.isEmpty && legacy.contains(LEGACY_KEY)) {
            manager.applicationLocales = LocaleList.forLanguageTags(normalize(legacy.getString(LEGACY_KEY, "").orEmpty()))
        }
    }

    fun currentDisplayName(context: Context): String {
        val selected = selectedTag(context)
        val shownTag = selected.ifBlank {
            if (Build.VERSION.SDK_INT >= 24) context.resources.configuration.locales[0].toLanguageTag()
            else @Suppress("DEPRECATION") context.resources.configuration.locale.toLanguageTag()
        }
        return displayName(context, Locale.forLanguageTag(shownTag))
    }

    /** Language the system default option follows, even when the app overrides it. */
    fun systemDisplayName(context: Context): String {
        val locales = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(android.app.LocaleManager::class.java).systemLocales
        } else {
            Resources.getSystem().configuration.locales
        }
        return displayName(context, locales[0] ?: Locale.getDefault(), useNativeName = false)
    }

    /** The name shown for an app language, written in the language itself by default. */
    fun displayName(context: Context, locale: Locale, useNativeName: Boolean = true): String {
        if (isSimplifiedChinese(locale)) return context.getString(R.string.app_language_simplified_chinese)
        if (!useNativeName) return locale.displayName
        return locale.getDisplayName(locale).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
    }

    /** Recognizes Simplified Chinese locales, including script-qualified tags such as zh-Hans-CN. */
    internal fun isSimplifiedChinese(locale: Locale): Boolean =
        locale.language == "zh" && (locale.script == "Hans" ||
            (locale.script.isEmpty() && locale.country in simplifiedChineseRegions))

    private fun normalize(tag: String): String {
        val normalized = tag.replace('_', '-')
        return when {
            normalized.equals("pt", true) -> "pt"
            normalized.startsWith("pt-BR", true) -> "pt-BR"
            supportedTags.any { it.equals(normalized, true) } -> supportedTags.first { it.equals(normalized, true) }
            normalized.isBlank() -> ""
            isSimplifiedChinese(Locale.forLanguageTag(normalized)) -> SIMPLIFIED_CHINESE
            else -> "en"
        }
    }
}
