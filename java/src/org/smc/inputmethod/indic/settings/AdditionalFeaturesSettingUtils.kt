/*
 * Copyright 2026, Jishnu Mohan <jishnu@gmail.com>
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
import android.content.SharedPreferences
import android.view.inputmethod.InputMethodSubtype

import androidx.preference.PreferenceFragmentCompat

import com.android.inputmethod.latin.RichInputMethodManager
import com.android.inputmethod.latin.RichInputMethodSubtype

/** Utility class for managing additional-features settings. */
object AdditionalFeaturesSettingUtils {
    const val ADDITIONAL_FEATURES_SETTINGS_SIZE = 0

    @JvmStatic
    fun addAdditionalFeaturesPreferences(
        context: Context, settingsFragment: PreferenceFragmentCompat
    ) {
        // do nothing.
    }

    @JvmStatic
    fun readAdditionalFeaturesPreferencesIntoArray(
        context: Context, prefs: SharedPreferences, additionalFeaturesPreferences: IntArray
    ) {
        // do nothing.
    }

    @JvmStatic
    fun createRichInputMethodSubtype(
        imm: RichInputMethodManager, subtype: InputMethodSubtype, context: Context
    ): RichInputMethodSubtype = RichInputMethodSubtype(subtype)
}
