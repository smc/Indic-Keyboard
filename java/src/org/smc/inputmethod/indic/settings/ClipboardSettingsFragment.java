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

import androidx.preference.Preference;

import com.android.inputmethod.latin.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.smc.inputmethod.indic.clipboard.ClipboardHistoryManager;

public final class ClipboardSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        addPreferencesFromResource(R.xml.prefs_screen_clipboard);

        final Preference clearHistory = findPreference("pref_clipboard_clear_history");
        clearHistory.setOnPreferenceClickListener(p -> {
            confirmClearHistory();
            return true;
        });
    }

    private void confirmClearHistory() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clipboard_clear_history)
                .setMessage(R.string.clipboard_clear_history_confirm)
                .setPositiveButton(R.string.clipboard_clear_all, (d, w) ->
                        ClipboardHistoryManager.init(requireContext()).clearHistory())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
