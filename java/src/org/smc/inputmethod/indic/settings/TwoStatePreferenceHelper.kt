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

import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat

import com.android.inputmethod.latin.R

object TwoStatePreferenceHelper {
    private const val EMPTY_TEXT = ""

    fun replaceCheckBoxPreferencesBySwitchPreferences(group: PreferenceGroup) {
        val preferences = (0 until group.preferenceCount).map { group.getPreference(it) }
        group.removeAll()
        for (preference in preferences) {
            if (preference is CheckBoxPreference) {
                addSwitchPreferenceBasedOnCheckBoxPreference(preference, group)
            } else {
                group.addPreference(preference)
                if (preference is PreferenceGroup) {
                    replaceCheckBoxPreferencesBySwitchPreferences(preference)
                }
            }
        }
    }

    private fun addSwitchPreferenceBasedOnCheckBoxPreference(
        checkBox: CheckBoxPreference, group: PreferenceGroup
    ) {
        val switchPref = SwitchPreferenceCompat(checkBox.context).apply {
            // Use the Material 3 switch widget instead of the default SwitchCompat.
            widgetLayoutResource = R.layout.preference_material_switch
            title = checkBox.title
            key = checkBox.key
            order = checkBox.order
            isPersistent = checkBox.isPersistent
            isEnabled = checkBox.isEnabled
            isChecked = checkBox.isChecked
            summary = checkBox.summary
            summaryOn = checkBox.summaryOn
            summaryOff = checkBox.summaryOff
            switchTextOn = EMPTY_TEXT
            switchTextOff = EMPTY_TEXT
        }
        group.addPreference(switchPref)
        switchPref.dependency = checkBox.dependency
    }
}
