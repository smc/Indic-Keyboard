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

import android.os.Bundle
import android.widget.Toast

import androidx.core.content.edit
import androidx.preference.Preference

import com.android.inputmethod.latin.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.smc.inputmethod.indic.clipboard.ClipboardHistoryManager

class PrivacySettingsFragment : SubScreenFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        addPreferencesFromResource(R.xml.prefs_screen_privacy)

        confirmOnClick(
            "pref_privacy_clear_emojis", R.string.privacy_clear_emojis_confirm,
            R.string.clipboard_clear_all, ::clearRecentEmojis
        )
        confirmOnClick(
            "pref_privacy_clear_clipboard", R.string.clipboard_clear_history_confirm,
            R.string.clipboard_clear_all
        ) { ClipboardHistoryManager.init(requireContext()).clearHistory() }
    }

    override fun onResume() {
        super.onResume()
        setActionBarTitle(getString(R.string.settings_screen_privacy))
    }

    private fun confirmOnClick(key: String, messageRes: Int, buttonRes: Int, action: () -> Unit) {
        val pref = requirePreference<Preference>(key)
        pref.setOnPreferenceClickListener { p ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(p.title)
                .setMessage(messageRes)
                .setPositiveButton(buttonRes) { _, _ ->
                    action()
                    Toast.makeText(requireContext(), R.string.privacy_deleted, Toast.LENGTH_SHORT)
                        .show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }

    private fun clearRecentEmojis() {
        sharedPreferences.edit { remove(Settings.PREF_EMOJI_RECENT_KEYS) }
    }
}
