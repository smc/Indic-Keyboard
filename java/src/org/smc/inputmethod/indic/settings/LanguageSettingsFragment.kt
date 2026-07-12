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

package org.smc.inputmethod.indic.settings

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue

import androidx.preference.Preference
import androidx.preference.PreferenceScreen

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.KeyboardLanguages
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils
import com.android.inputmethod.latin.utils.TextDrawable

import kotlin.math.roundToInt

class LanguageSettingsFragment : SubScreenFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        setActionBarTitle(getString(R.string.language_selection_title))
        // A language moves between the enabled and available sections after its layouts are
        // edited, so rebuild the whole screen each time we return to it.
        buildScreen()
    }

    private fun buildScreen() {
        val context = requireContext()
        val screen = preferenceManager.createPreferenceScreen(context)
        preferenceScreen = screen

        val enabledKeys = Settings.readEnabledSubtypeKeys(sharedPreferences)
        // English-first, then case-insensitive by English name.
        val byName = compareByDescending<Language> { it.mLocale.startsWith("en") }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.mEnglishName }
        val (enabled, available) = KeyboardLanguages.getLanguages(context)
            .partition { isEnabled(it, enabledKeys) }

        addSection(context, screen, R.string.language_section_enabled, enabled.sortedWith(byName))
        addSection(context, screen, R.string.language_section_available, available.sortedWith(byName))
    }

    private fun addSection(
        context: Context, screen: PreferenceScreen, titleResId: Int, languages: List<Language>
    ) {
        if (languages.isEmpty()) return
        val category = screen.addCategory(context, titleResId)
        for (language in languages) {
            val pref = Preference(context)
            pref.title = formatName(language)
            pref.icon = createIcon(context, language)
            pref.fragment = LanguageLayoutSettingsFragment::class.java.name
            pref.extras.putString(LanguageLayoutSettingsFragment.EXTRA_LOCALE, language.mLocale)
            category.addPreference(pref)
        }
    }

    companion object {
        private fun isEnabled(language: Language, enabledKeys: Set<String>): Boolean =
            language.mLayouts.any {
                enabledKeys.contains(SubtypeLocaleUtils.getSubtypeKey(it.mSubtype))
            }

        private fun formatName(language: Language): CharSequence =
            if (language.mEnglishName.equals(language.mAutonym, ignoreCase = true)) {
                language.mEnglishName
            } else {
                "${language.mEnglishName} (${language.mAutonym})"
            }

        private fun createIcon(context: Context, language: Language): Drawable {
            val accent = resolveColor(context, androidx.appcompat.R.attr.colorPrimary)
            val size = (40 * context.resources.displayMetrics.density).roundToInt()
            return TextDrawable(language.mGlyph, accent, 0, size)
        }

        private fun resolveColor(context: Context, attr: Int): Int {
            val value = TypedValue()
            context.theme.resolveAttribute(attr, value, true)
            return value.data
        }
    }
}
