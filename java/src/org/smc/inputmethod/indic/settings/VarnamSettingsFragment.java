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

import java.util.HashMap;
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
        Context context = getPreferenceManager().getContext();
        androidx.preference.PreferenceScreen preferenceScreen = this.getPreferenceScreen();

        HashMap<String, VarnamIndicKeyboard.Scheme> installedLanguages = VarnamIndicKeyboard.getInstalledSchemes(context);

        // Available languages (VST file installed to use)
        PreferenceCategory enabledPreferenceCategory = new PreferenceCategory(preferenceScreen.getContext());
        enabledPreferenceCategory.setTitle(R.string.pref_varnam_category_enabled);
        enabledPreferenceCategory.setIconSpaceReserved(false);
        preferenceScreen.addPreference(enabledPreferenceCategory);

        for (Map.Entry<String, VarnamIndicKeyboard.Scheme> entry : installedLanguages.entrySet()) {
            String keyboardID = entry.getKey();
            VarnamIndicKeyboard.Scheme scheme = entry.getValue();

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
            screen.setTitle(scheme.name);
            screen.setFragment("org.smc.inputmethod.indic.settings.VarnamSettingsLangFragment");
            screen.setIconSpaceReserved(false);
            screen.getExtras().putString("id", keyboardID);

            enabledPreferenceCategory.addPreference(screen);
        }

        // Languages
        PreferenceCategory disabledPreferenceCategory = new PreferenceCategory(preferenceScreen.getContext());
        disabledPreferenceCategory.setTitle(R.string.pref_varnam_category_disabled);
        disabledPreferenceCategory.setIconSpaceReserved(false);
        preferenceScreen.addPreference(disabledPreferenceCategory);

        for (Map.Entry<String, VarnamIndicKeyboard.Scheme> entry : VarnamIndicKeyboard.schemes.entrySet()) {
            String keyboardID = entry.getKey();

            if (installedLanguages.containsKey(keyboardID)) {
                continue;
            }

            VarnamIndicKeyboard.Scheme scheme = entry.getValue();

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
            screen.setTitle(scheme.name);
            screen.setFragment("org.smc.inputmethod.indic.settings.VarnamSettingsLangFragment");
            screen.setIconSpaceReserved(false);
            screen.getExtras().putString("id", keyboardID);

            disabledPreferenceCategory.addPreference(screen);
        }
    }
}
