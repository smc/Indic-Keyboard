/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.smc.inputmethod.indic.themes;

import android.preference.PreferenceActivity;
import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import org.smc.inputmethod.indic.R;
import org.smc.inputmethod.indic.settings.Settings;


public final class ThemesActivity extends PreferenceActivity {
    public static final String EXTRA_SHOW_HOME_AS_UP = "show_home_as_up";
    private boolean mShowHomeAsUp;

    @Override
    protected void onCreate(final Bundle savedState) {
        super.onCreate(savedState);
        final ActionBar actionBar = getActionBar();
        actionBar.setTitle("Select Theme");
        addPreferencesFromResource(R.xml.prefs_screen_appearance);

    }
    @Override
    public void onResume() {
        super.onResume();
        CustomInputStylesSettingsFragment.updateCustomInputStylesSummary(
                                                                         findPreference(Settings.PREF_CUSTOM_INPUT_STYLES));
        ThemeSettingsFragment.updateKeyboardThemeSummary(findPreference(Settings.SCREEN_THEME));
    }


}
