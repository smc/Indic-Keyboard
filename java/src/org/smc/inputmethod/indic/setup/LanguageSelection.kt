/*
 * Copyright 2026, Jishnu Mohan <jishnu7@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.smc.inputmethod.indic.setup

import android.content.Context

import com.android.inputmethod.compat.PreferenceManagerCompat
import com.android.inputmethod.latin.RichInputMethodManager
import com.android.inputmethod.latin.common.LocaleUtils
import com.android.inputmethod.latin.utils.KeyboardLanguages
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils

import org.smc.inputmethod.indic.settings.Settings as AppSettings

/** The wizard's language/layout selection state: which locales the user picked, which subtype
 *  keys are enabled, and how to reconcile and commit them. */
internal class LanguageSelection(private val context: Context) {

    var loaded = false
        private set
    var languages: List<Language> = emptyList()
        private set
    val selectedLocales = LinkedHashSet<String>()
    val enabledKeys = HashSet<String>()

    fun ensureLoaded() {
        if (loaded) {
            return
        }
        loaded = true
        RichInputMethodManager.init(context)
        languages = KeyboardLanguages.getLanguages(context)
        enabledKeys.addAll(
            AppSettings.readEnabledSubtypeKeys(
                PreferenceManagerCompat.getDeviceSharedPreferences(context)
            )
        )
        for (language in languages) {
            for (layout in language.mLayouts) {
                if (enabledKeys.contains(SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype))) {
                    selectedLocales.add(language.mLocale)
                    break
                }
            }
        }
    }

    /** English first, then alphabetical by English name. */
    fun sorted(): List<Language> = languages.sortedWith { a, b ->
        val aEn = a.mLocale.startsWith("en")
        val bEn = b.mLocale.startsWith("en")
        when {
            aEn != bEn -> if (aEn) -1 else 1
            else -> a.mEnglishName.compareTo(b.mEnglishName, ignoreCase = true)
        }
    }

    fun sortedSelected(): List<Language> = sorted().filter { selectedLocales.contains(it.mLocale) }

    /** Selecting a language guarantees at least its first layout; deselecting drops them all. */
    fun reconcileEnabledWithSelection() {
        for (language in languages) {
            val selected = selectedLocales.contains(language.mLocale)
            val hasEnabled = language.mLayouts.any {
                enabledKeys.contains(SubtypeLocaleUtils.getSubtypeKey(it.mSubtype))
            }
            if (selected && !hasEnabled && language.mLayouts.isNotEmpty()) {
                enabledKeys.add(SubtypeLocaleUtils.getSubtypeKey(language.mLayouts[0].mSubtype))
            } else if (!selected && hasEnabled) {
                for (layout in language.mLayouts) {
                    enabledKeys.remove(SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype))
                }
            }
        }
    }

    fun commitEnabledLayouts() {
        reconcileEnabledWithSelection()
        RichInputMethodManager.init(context)
        RichInputMethodManager.getInstance().setEnabledSubtypeKeys(enabledKeys)
    }

    fun languageCode(language: Language): String =
        LocaleUtils.constructLocaleFromString(language.mLocale).language

    fun displayName(code: String): CharSequence {
        for (language in languages) {
            if (code == languageCode(language)) {
                return language.mEnglishName
            }
        }
        return code
    }

    fun formatName(language: Language): CharSequence =
        if (language.mEnglishName.equals(language.mAutonym, ignoreCase = true)) {
            language.mEnglishName
        } else {
            "${language.mEnglishName} (${language.mAutonym})"
        }
}
