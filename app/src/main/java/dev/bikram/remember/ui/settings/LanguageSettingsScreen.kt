package dev.bikram.remember.ui.settings

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import java.util.Locale

private val LOCALE_MANAGER_CLASS = "android.os.LocaleManager"
private val LOCALE_SERVICE = "locale"

private data class LanguageOption(
    val tag: String,
    val displayLabel: String,
)

private val languageOptions = listOf(
    LanguageOption("", "System default"),
    LanguageOption("en", "English"),
    LanguageOption("es", "Español"),
    LanguageOption("fr", "Français"),
    LanguageOption("de", "Deutsch"),
    LanguageOption("pt", "Português"),
    LanguageOption("it", "Italiano"),
)

private fun getCurrentLanguageTag(context: Context): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val localeManager = context.getSystemService(LOCALE_SERVICE)
        if (localeManager != null) {
            try {
                val applicationLocalesMethod = localeManager.javaClass.getMethod("getApplicationLocales")
                val locales = applicationLocalesMethod.invoke(localeManager) as LocaleList?
                if (locales != null && locales.size() > 0) {
                    locales.get(0).toLanguageTag()
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    } else {
        Locale.getDefault().toLanguageTag()
    }
}

private fun setLanguage(context: Context, tag: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val localeManager = context.getSystemService(LOCALE_SERVICE)
        if (localeManager != null) {
            try {
                if (tag.isEmpty()) {
                    val setApplicationLocalesMethod = localeManager.javaClass.getMethod("setApplicationLocales", LocaleList::class.java)
                    setApplicationLocalesMethod.invoke(localeManager, LocaleList.getEmptyLocaleList())
                } else {
                    val localeList = LocaleList.forLanguageTags(tag)
                    val setApplicationLocalesMethod = localeManager.javaClass.getMethod("setApplicationLocales", LocaleList::class.java)
                    setApplicationLocalesMethod.invoke(localeManager, localeList)
                }
            } catch (e: Exception) {
                // Fallback to old method
                val locale = if (tag.isEmpty()) Locale.getDefault() else Locale(tag)
                Locale.setDefault(locale)
                @Suppress("DEPRECATION")
                val config = context.resources.configuration
                config.setLocale(locale)
                context.createConfigurationContext(config).resources
                (context as? Activity)?.recreate()
            }
        }
    } else {
        val locale = if (tag.isEmpty()) Locale.getDefault() else Locale(tag)
        Locale.setDefault(locale)
        @Suppress("DEPRECATION")
        val config = context.resources.configuration
        config.setLocale(locale)
        context.createConfigurationContext(config).resources
        (context as? Activity)?.recreate()
    }
}

@Composable
internal fun LanguageSection() {
    val context = LocalContext.current
    var selectedTag by remember { mutableStateOf(getCurrentLanguageTag(context)) }
    val systemDefaultLabel = stringResource(R.string.settings_language_system_default)

    GroupedListColumn {
        languageOptions.forEachIndexed { index, option ->
            GroupedListItem(
                position = when (index) {
                    0 -> GroupPosition.FIRST
                    languageOptions.lastIndex -> GroupPosition.LAST
                    else -> GroupPosition.MIDDLE
                },
            ) {
                val label = if (option.tag.isEmpty()) systemDefaultLabel else option.displayLabel
                val isSelected = selectedTag == option.tag

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTag = option.tag
                            setLanguage(context, option.tag)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            selectedTag = option.tag
                            setLanguage(context, option.tag)
                        },
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}
