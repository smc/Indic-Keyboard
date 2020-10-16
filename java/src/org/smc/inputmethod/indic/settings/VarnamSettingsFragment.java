/*
 * Copyright (C) 2020, Subin Siby <mail@subinsb.com>
 * Licensed under the Apache License, Version 2.0
 */

package org.smc.inputmethod.indic.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.android.inputmethod.latin.R;

import org.smc.inputmethod.indic.inputlogic.VarnamIndicKeyboard;

import java.util.Map;

/**
 * "Varnam" language settings sub screen.
 * Allows user to configure varnam languages here
 */
public final class VarnamSettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.prefs_screen_varnam);
        setupVarnamLangs();
    }

    private void setupVarnamLangs() {
        androidx.preference.PreferenceScreen preferenceScreen = this.getPreferenceScreen();

        // Enabled languages
        PreferenceCategory enabledPreferenceCategory = new PreferenceCategory(preferenceScreen.getContext());
        enabledPreferenceCategory.setTitle(R.string.pref_varnam_category_enabled);
        preferenceScreen.addPreference(enabledPreferenceCategory);

        // Available languages
        PreferenceCategory disabledPreferenceCategory = new PreferenceCategory(preferenceScreen.getContext());
        disabledPreferenceCategory.setTitle(R.string.pref_varnam_category_disabled);
        preferenceScreen.addPreference(disabledPreferenceCategory);

        Context context = getPreferenceManager().getContext();

        for (Map.Entry<String, VarnamIndicKeyboard.Scheme> entry : VarnamIndicKeyboard.schemes.entrySet()) {
            String keyboardIdentifier = entry.getKey();
            VarnamIndicKeyboard.Scheme scheme = entry.getValue();

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
            screen.setTitle(scheme.name);
            screen.setFragment("org.smc.inputmethod.indic.settings.VarnamSettingsLangFragment");
            screen.getExtras().putString("id", keyboardIdentifier);

            disabledPreferenceCategory.addPreference(screen);
        }
    }
}
