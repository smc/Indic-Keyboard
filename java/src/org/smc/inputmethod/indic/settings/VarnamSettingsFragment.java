/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.util.Log;

import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SystemBroadcastReceiver;

/**
 * "Varnam" settings sub screen.
 *
 * This settings sub screen handles the following varnam preferences.
 * - TODO Language download
 * - Import words
 */
public final class VarnamSettingsFragment extends SubScreenFragment {
    private static int PICK_VARNAM_CORPUS_FILE = 1;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_varnam);

        setupVarnamImportFile();
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        final Resources res = getResources();
        if (key.equals(Settings.PREF_POPUP_ON)) {
            setPreferenceEnabled(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                    Settings.readKeyPreviewPopupEnabled(prefs, res));
        } else if (key.equals(Settings.PREF_SHOW_SETUP_WIZARD_ICON)) {
            SystemBroadcastReceiver.toggleAppIcon(getActivity());
        }
        updateListPreferenceSummaryToCurrentValue(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == PICK_VARNAM_CORPUS_FILE && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                // Perform operations on the document using its URI.
                Log.d("Varnam", "Learn file : " + uri);
            }
        }
    }

    private void setupVarnamImportFile() {
        Preference filePicker = findPreference("pref_varnam_import_file");
        filePicker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                // TODO https://stackoverflow.com/questions/8945531/pick-any-kind-of-file-via-an-intent-in-android
                // TODO might not work in some file explorers ?
                intent.putExtra("CONTENT_TYPE", "*/*");
                startActivityForResult(intent, PICK_VARNAM_CORPUS_FILE);
                return true;
            }
        });
    }
}
