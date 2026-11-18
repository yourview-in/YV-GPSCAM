package `in`.yourview.yvgpscam.util

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    val SUPPORTED_LANGUAGES = listOf("English", "Hindi", "Telugu", "Spanish", "French")

    fun languageTagForName(languageName: String): String = when (languageName.trim().lowercase(Locale.US)) {
        "hindi", "हिन्दी", "hi" -> "hi"
        "telugu", "తెలుగు", "te" -> "te"
        "spanish", "español", "es" -> "es"
        "french", "français", "fr" -> "fr"
        else -> "en"
    }

    fun languageNameForTag(tag: String): String = when (tag.trim().lowercase(Locale.US).substringBefore("-")) {
        "hi" -> "Hindi"
        "te" -> "Telugu"
        "es" -> "Spanish"
        "fr" -> "French"
        else -> "English"
    }

    fun applyLocaleToContext(base: Context, languageName: String): Context {
        val tag = languageTagForName(languageName)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        val localized = base.createConfigurationContext(config)
        return LocalizedContextWrapper(base, localized)
    }

    fun syncSystemPerAppLocale(context: Context, languageName: String) {
        val tag = languageTagForName(languageName)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val manager = context.getSystemService(LocaleManager::class.java)
                manager?.applicationLocales = LocaleList.forLanguageTags(tag)
            }
        }
    }

    private class LocalizedContextWrapper(
        base: Context,
        private val configContext: Context
    ) : ContextWrapper(base) {
        override fun getResources(): Resources = configContext.resources
        override fun getAssets(): AssetManager = configContext.assets
    }
}
