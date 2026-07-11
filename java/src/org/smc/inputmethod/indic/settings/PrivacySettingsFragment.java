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

package org.smc.inputmethod.indic.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;

import com.android.inputmethod.latin.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.smc.inputmethod.indic.clipboard.ClipboardHistoryManager;
import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager;
import org.smc.inputmethod.indic.personalization.PersonalizationHelper;
import org.smc.inputmethod.indic.varnam.VarnamIndicKeyboard;

import java.io.File;

/**
 * "Privacy" settings sub screen: deletes what the keyboard has learned or remembered — user
 * history dictionaries, Varnam learnings, recent emojis and clipboard history.
 */
public final class PrivacySettingsFragment extends SubScreenFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        addPreferencesFromResource(R.xml.prefs_screen_privacy);

        confirmOnClick("pref_privacy_delete_typed", R.string.privacy_learned_typing_confirm,
                R.string.privacy_delete, this::deleteLearnedWords);
        confirmOnClick("pref_privacy_delete_varnam", R.string.privacy_learned_varnam_confirm,
                R.string.privacy_delete, this::deleteVarnamLearnings);
        confirmOnClick("pref_privacy_clear_emojis", R.string.privacy_clear_emojis_confirm,
                R.string.clipboard_clear_all, this::clearRecentEmojis);
        confirmOnClick("pref_privacy_clear_clipboard", R.string.clipboard_clear_history_confirm,
                R.string.clipboard_clear_all, () ->
                        ClipboardHistoryManager.init(requireContext()).clearHistory());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.settings_screen_privacy);
            }
        }
    }

    private void confirmOnClick(final String key, final int messageRes, final int buttonRes,
            final Runnable action) {
        final Preference pref = findPreference(key);
        pref.setOnPreferenceClickListener(p -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(p.getTitle())
                    .setMessage(messageRes)
                    .setPositiveButton(buttonRes, (dialog, which) -> {
                        action.run();
                        Toast.makeText(requireContext(), R.string.privacy_deleted,
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
    }

    private void deleteLearnedWords() {
        PersonalizationHelper.removeAllUserHistoryDictionaries(requireContext());
    }

    private void deleteVarnamLearnings() {
        for (final VarnamIndicKeyboard.Scheme scheme : VarnamIndicKeyboard.schemes.values()) {
            final File dir = LanguagePackDownloadManager.packDir(requireContext(), scheme.lang);
            final File[] learnings = dir.listFiles(
                    (d, name) -> name.startsWith(scheme.lang + ".learnings"));
            if (learnings != null) {
                for (final File file : learnings) {
                    file.delete();
                }
            }
            // Without the marker the engine re-imports the downloaded .vlf word packs into the
            // fresh learnings DB, so only the user's own words are lost.
            LanguagePackDownloadManager.importMarker(requireContext(), scheme.lang).delete();
        }
        VarnamIndicKeyboard.onLearningsCleared();
    }

    private void clearRecentEmojis() {
        getSharedPreferences().edit().remove(Settings.PREF_EMOJI_RECENT_KEYS).apply();
    }
}
