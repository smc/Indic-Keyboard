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

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle

import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.permissions.PermissionsManager
import com.android.inputmethod.latin.permissions.PermissionsUtil

import org.smc.inputmethod.indic.userdictionary.UserDictionaryList
import org.smc.inputmethod.indic.userdictionary.UserDictionarySettings

/**
 * "Text correction" settings sub screen: personal & add-on dictionaries, offensive-word blocking,
 * auto-correction, suggestions, personalized/contact/next-word suggestions.
 */
class CorrectionSettingsFragment : SubScreenFragment(),
    PermissionsManager.PermissionsResultCallback {

    private lateinit var useContactsPreference: SwitchPreferenceCompat

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        addPreferencesFromResource(R.xml.prefs_screen_correction)

        val pm = requireActivity().packageManager

        // IndicKeyboard: dictionary download is not supported yet, so always drop the link.
        removePreference(Settings.PREF_CONFIGURE_DICTIONARIES_KEY)

        val editPersonalDictionary =
            requirePreference<Preference>(Settings.PREF_EDIT_PERSONAL_DICTIONARY)
        val ri = if (USE_INTERNAL_PERSONAL_DICTIONARY_SETTINGS) null
        else pm.resolveActivity(editPersonalDictionary.intent!!, PackageManager.MATCH_DEFAULT_ONLY)
        if (ri == null) {
            overwriteUserDictionaryPreference(editPersonalDictionary)
        }

        useContactsPreference = requirePreference(Settings.PREF_KEY_USE_CONTACTS_DICT)
        turnOffUseContactsIfNoPermission()
    }

    private fun overwriteUserDictionaryPreference(userDictionaryPreference: Preference) {
        val localeList = UserDictionaryList.getUserDictionaryLocalesSet(requireActivity())
        when {
            // A null list means the user dictionary service is absent or disabled; drop the pref.
            localeList == null -> preferenceScreen.removePreference(userDictionaryPreference)
            localeList.size <= 1 -> {
                userDictionaryPreference.fragment = UserDictionarySettings::class.java.name
                // With no locale extra, UserDictionarySettings interprets it as "current locale".
                if (localeList.size == 1) {
                    userDictionaryPreference.extras.putString("locale", localeList.first())
                }
            }
            else -> userDictionaryPreference.fragment = UserDictionaryList::class.java.name
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key != Settings.PREF_KEY_USE_CONTACTS_DICT) return
        if (!prefs.getBoolean(key, false)) {
            // don't care if the preference is turned off.
            return
        }
        if (PermissionsUtil.checkAllPermissionsGranted(
                requireActivity(), Manifest.permission.READ_CONTACTS
            )
        ) {
            return // all permissions granted, no need to request permissions.
        }
        PermissionsManager.get(requireActivity()).requestPermissions(
            this, requireActivity(), Manifest.permission.READ_CONTACTS
        )
    }

    override fun onRequestPermissionsResult(allGranted: Boolean) {
        turnOffUseContactsIfNoPermission()
    }

    private fun turnOffUseContactsIfNoPermission() {
        if (!PermissionsUtil.checkAllPermissionsGranted(
                requireActivity(), Manifest.permission.READ_CONTACTS
            )
        ) {
            useContactsPreference.isChecked = false
        }
    }

    companion object {
        private const val USE_INTERNAL_PERSONAL_DICTIONARY_SETTINGS = false
    }
}
