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

package org.smc.inputmethod.indic.spellcheck

import android.Manifest
import android.content.SharedPreferences
import android.os.Bundle

import androidx.preference.SwitchPreferenceCompat

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.permissions.PermissionsManager
import com.android.inputmethod.latin.permissions.PermissionsUtil
import com.android.inputmethod.latin.utils.ApplicationUtils

import org.smc.inputmethod.indic.settings.SubScreenFragment
import org.smc.inputmethod.indic.settings.TwoStatePreferenceHelper

/** Spell-checker preference screen. */
class SpellCheckerSettingsFragment : SubScreenFragment(),
    PermissionsManager.PermissionsResultCallback {

    private lateinit var lookupContactsPreference: SwitchPreferenceCompat

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        addPreferencesFromResource(R.xml.spell_checker_settings)
        val screen = preferenceScreen
        screen.setTitle(
            ApplicationUtils.getActivityTitleResId(
                requireActivity(), SpellCheckerSettingsActivity::class.java
            )
        )
        TwoStatePreferenceHelper.replaceCheckBoxPreferencesBySwitchPreferences(screen)

        lookupContactsPreference = findPreference(AndroidSpellCheckerService.PREF_USE_CONTACTS_KEY)!!
        turnOffLookupContactsIfNoPermission()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key != AndroidSpellCheckerService.PREF_USE_CONTACTS_KEY) return
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
        turnOffLookupContactsIfNoPermission()
    }

    private fun turnOffLookupContactsIfNoPermission() {
        if (!PermissionsUtil.checkAllPermissionsGranted(
                requireActivity(), Manifest.permission.READ_CONTACTS
            )
        ) {
            lookupContactsPreference.isChecked = false
        }
    }
}
