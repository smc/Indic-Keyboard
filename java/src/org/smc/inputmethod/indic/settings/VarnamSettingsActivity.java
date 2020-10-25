/*
 * Copyright (C) 2020, Subin Siby <mail@subinsb.com>
 * Licensed under the Apache License, Version 2.0
 */

package org.smc.inputmethod.indic.settings;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.inputmethod.latin.R;

public class VarnamSettingsActivity extends FragmentActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.varnam);
        findViewById(R.id.varnam_import_file_status).setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new Fragment
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.varnam_settings_fragment, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }
}
