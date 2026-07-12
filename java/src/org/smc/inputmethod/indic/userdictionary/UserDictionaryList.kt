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

package org.smc.inputmethod.indic.userdictionary

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.provider.UserDictionary
import android.text.TextUtils
import android.view.inputmethod.InputMethodManager

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.LocaleUtils

import java.util.Locale
import java.util.TreeSet

// Caveat: this class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryList.java
// in order to deal with some devices that have issues with the user dictionary handling.
@Suppress("DEPRECATION")
class UserDictionaryList : PreferenceFragment() {

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        preferenceScreen = preferenceManager.createPreferenceScreen(activity)
    }

    /** Creates the entries that let the user open the user dictionary for each locale. */
    private fun createUserDictSettings(userDictGroup: PreferenceGroup) {
        userDictGroup.removeAll()
        val localeSet = getUserDictionaryLocalesSet(activity!!)!!

        if (localeSet.size > 1) {
            // Have an "All languages" entry if there are two or more active languages.
            localeSet.add("")
        }

        if (localeSet.isEmpty()) {
            userDictGroup.addPreference(createUserDictionaryPreference(null))
        } else {
            for (locale in localeSet) {
                userDictGroup.addPreference(createUserDictionaryPreference(locale))
            }
        }
    }

    /** Create a single user-dictionary preference for the given locale. */
    private fun createUserDictionaryPreference(localeString: String?): Preference {
        val newPref = Preference(activity)
        val intent = Intent(USER_DICTIONARY_SETTINGS_INTENT_ACTION)
        if (localeString == null) {
            newPref.title = Locale.getDefault().displayName
        } else {
            newPref.title = if (localeString.isEmpty()) {
                getString(R.string.user_dict_settings_all_languages)
            } else {
                LocaleUtils.constructLocaleFromString(localeString).displayName
            }
            intent.putExtra("locale", localeString)
            newPref.extras.putString("locale", localeString)
        }
        newPref.intent = intent
        newPref.fragment = UserDictionarySettings::class.java.name
        return newPref
    }

    override fun onResume() {
        super.onResume()
        createUserDictSettings(preferenceScreen)
    }

    companion object {
        const val USER_DICTIONARY_SETTINGS_INTENT_ACTION =
            "android.settings.USER_DICTIONARY_SETTINGS"

        @JvmStatic
        fun getUserDictionaryLocalesSet(activity: Activity): TreeSet<String>? {
            val cursor = activity.contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.LOCALE), null, null, null
            ) ?: return null // The user dictionary service is not present or disabled.
            val localeSet = TreeSet<String>()
            try {
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(UserDictionary.Words.LOCALE)
                    do {
                        val locale = cursor.getString(columnIndex)
                        localeSet.add(locale ?: "")
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor.close()
            }
            if (!UserDictionarySettings.IS_SHORTCUT_API_SUPPORTED) {
                // For ICS, show "For all languages" in case the keyboard locale differs from the
                // system locale.
                localeSet.add("")
            }

            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            for (imi in imm.enabledInputMethodList) {
                val subtypes =
                    imm.getEnabledInputMethodSubtypeList(imi, true /* allowsImplicitly... */)
                for (subtype in subtypes) {
                    val locale = subtype.locale
                    if (!TextUtils.isEmpty(locale)) {
                        localeSet.add(locale)
                    }
                }
            }

            // We come here after collecting locales from existing entries and enabled subtypes.
            // If we already have the language-only version of the system locale, we don't add the
            // system locale to avoid confusion even though it's technically correct to add it.
            if (!localeSet.contains(Locale.getDefault().language.toString())) {
                localeSet.add(Locale.getDefault().toString())
            }
            return localeSet
        }
    }
}
