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

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences

import com.android.inputmethod.compat.PreferenceManagerCompat
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.permissions.PermissionsManager
import com.android.inputmethod.latin.permissions.PermissionsUtil
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language
import com.google.android.material.materialswitch.MaterialSwitch

import org.smc.inputmethod.indic.settings.Settings as AppSettings
import org.smc.inputmethod.indic.varnam.VarnamIndicKeyboard

/** Builds the wizard's personalize step: companion-language rows (radio behavior over the
 *  single-valued pref) and the contact-names row with its runtime-permission handshake. */
internal class PersonalizeStepController(
    private val activity: Activity,
    private val selection: LanguageSelection
) : PermissionsManager.PermissionsResultCallback {

    private val companionSwitches = HashMap<String, MaterialSwitch>()
    private var contactsSwitch: MaterialSwitch? = null

    fun buildList(list: SetupSelectionList) {
        selection.ensureLoaded()
        list.clear()
        val prefs = PreferenceManagerCompat.getDeviceSharedPreferences(activity)
        addCompanionRows(list, prefs)
        addContactsRow(list, prefs)
    }

    /** Selected languages with a Varnam scheme; empty unless English is also selected, since
     *  companion suggestions only appear on the English keyboard. */
    private fun companionCandidates(): List<Language> {
        val selected = selection.sortedSelected()
        if (selected.none { selection.languageCode(it) == "en" }) {
            return emptyList()
        }
        return selected.filter {
            VarnamIndicKeyboard.schemes.containsKey("varnam-${selection.languageCode(it)}")
        }
    }

    private fun addCompanionRows(list: SetupSelectionList, prefs: SharedPreferences) {
        val candidates = companionCandidates()
        if (candidates.isEmpty()) {
            return
        }
        var companion = AppSettings.readCompanionLanguage(prefs)
        // A lone candidate starts on the first time through; once the key exists, later runs
        // respect whatever the user chose.
        if (candidates.size == 1 && !prefs.contains(AppSettings.PREF_COMPANION_LANGUAGE)) {
            companion = selection.languageCode(candidates[0])
            prefs.edit().putString(AppSettings.PREF_COMPANION_LANGUAGE, companion).apply()
        }
        list.addHeader(activity.getString(R.string.su_section_companion))
        companionSwitches.clear()
        for (language in candidates) {
            val code = selection.languageCode(language)
            val row = list.addRow(
                activity.getString(R.string.su_companion_row_title, language.mEnglishName),
                icon = list.glyphIcon(language),
                summary = activity.getString(
                    R.string.companion_language_suggestions_summary, language.mAutonym
                ),
                checked = code == companion
            ) { checked ->
                prefs.edit()
                    .putString(AppSettings.PREF_COMPANION_LANGUAGE, if (checked) code else "")
                    .apply()
                if (checked) {
                    // The pref holds a single language: checking one row unchecks the rest.
                    for ((otherCode, otherSwitch) in companionSwitches) {
                        if (otherCode != code) otherSwitch.isChecked = false
                    }
                }
            }
            companionSwitches[code] = row.findViewById(R.id.selection_switch)
        }
    }

    private fun addContactsRow(list: SetupSelectionList, prefs: SharedPreferences) {
        list.addHeader(activity.getString(R.string.su_section_suggestions))
        val granted = PermissionsUtil.checkAllPermissionsGranted(
            activity, Manifest.permission.READ_CONTACTS
        )
        val row = list.addRow(
            activity.getString(R.string.use_contacts_dict),
            summary = activity.getString(R.string.use_contacts_dict_summary),
            checked = granted && prefs.getBoolean(AppSettings.PREF_KEY_USE_CONTACTS_DICT, true)
        ) { checked ->
            prefs.edit().putBoolean(AppSettings.PREF_KEY_USE_CONTACTS_DICT, checked).apply()
            if (checked && !PermissionsUtil.checkAllPermissionsGranted(
                    activity, Manifest.permission.READ_CONTACTS
                )
            ) {
                PermissionsManager.get(activity)
                    .requestPermissions(this, activity, Manifest.permission.READ_CONTACTS)
            }
        }
        contactsSwitch = row.findViewById(R.id.selection_switch)
    }

    override fun onRequestPermissionsResult(allGranted: Boolean) {
        if (!allGranted) {
            contactsSwitch?.isChecked = false
            PreferenceManagerCompat.getDeviceSharedPreferences(activity).edit()
                .putBoolean(AppSettings.PREF_KEY_USE_CONTACTS_DICT, false).apply()
        }
    }
}
