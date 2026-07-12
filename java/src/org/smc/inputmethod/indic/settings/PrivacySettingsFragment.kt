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

import androidx.appcompat.app.AppCompatActivity

import com.android.inputmethod.latin.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.smc.inputmethod.indic.clipboard.ClipboardHistoryManager
import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager
import org.smc.inputmethod.indic.varnam.VarnamIndicKeyboard

/**
 * "Privacy" settings sub screen: deletes what the keyboard has learned or remembered — user
 * history dictionaries, Varnam learnings, recent emojis and clipboard history.
 */
class PrivacySettingsFragment : SubScreenFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        addPreferencesFromResource(R.xml.prefs_screen_privacy)

        confirmOnClick(
            "pref_privacy_delete_varnam", R.string.privacy_learned_varnam_confirm,
            R.string.privacy_delete, ::deleteVarnamLearnings
        )
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
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.settings_screen_privacy)
    }

    private fun confirmOnClick(key: String, messageRes: Int, buttonRes: Int, action: () -> Unit) {
        val pref = findPreference<androidx.preference.Preference>(key)!!
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

    private fun deleteVarnamLearnings() {
        for (scheme in VarnamIndicKeyboard.schemes.values) {
            val dir = LanguagePackDownloadManager.packDir(requireContext(), scheme.lang)
            dir.listFiles { _, name -> name.startsWith(scheme.lang + ".learnings") }
                ?.forEach { it.delete() }
            // Without the marker the engine re-imports the downloaded .vlf word packs into the
            // fresh learnings DB, so only the user's own words are lost.
            LanguagePackDownloadManager.importMarker(requireContext(), scheme.lang).delete()
        }
        VarnamIndicKeyboard.onLearningsCleared()
    }

    private fun clearRecentEmojis() {
        getSharedPreferences().edit().remove(Settings.PREF_EMOJI_RECENT_KEYS).apply()
    }
}
