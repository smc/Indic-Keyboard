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

package org.smc.inputmethod.indic.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.permissions.PermissionsManager;
import com.google.android.material.appbar.MaterialToolbar;

public final class SettingsActivity extends AppCompatActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        ActivityCompat.OnRequestPermissionsResultCallback {
    // Kept compatible with the framework PreferenceActivity extra so external deep links and the
    // system "App info" entry continue to open a specific fragment.
    public static final String EXTRA_SHOW_FRAGMENT = ":android:show_fragment";

    public static final String EXTRA_ENTRY_KEY = "entry";
    public static final String EXTRA_ENTRY_VALUE_APP_ICON = "app_icon";
    public static final String EXTRA_ENTRY_VALUE_NOTICE_DIALOG = "important_notice";
    public static final String EXTRA_ENTRY_VALUE_SYSTEM_SETTINGS = "system_settings";

    private CharSequence mDefaultTitle;

    @Override
    protected void onCreate(final Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.settings_activity);
        final MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        mDefaultTitle = getTitle();

        getSupportFragmentManager().addOnBackStackChangedListener(this::updateTitle);

        if (savedState == null) {
            final String fragmentName = getIntent().getStringExtra(EXTRA_SHOW_FRAGMENT);
            final Fragment fragment = (fragmentName != null)
                    ? instantiate(fragmentName, null) : new SettingsFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, fragment)
                    .commit();
        }
    }

    private Fragment instantiate(final String name, final Bundle args) {
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), name);
        if (args != null) {
            args.setClassLoader(fragment.getClass().getClassLoader());
            fragment.setArguments(args);
        }
        return fragment;
    }

    private void updateTitle() {
        if (getSupportActionBar() == null) {
            return;
        }
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            getSupportActionBar().setTitle(mDefaultTitle);
        }
    }

    @Override
    public boolean onPreferenceStartFragment(final PreferenceFragmentCompat caller,
            final Preference pref) {
        final Fragment fragment = instantiate(pref.getFragment(), pref.getExtras());
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, fragment)
                .addToBackStack(null)
                .commit();
        if (getSupportActionBar() != null && pref.getTitle() != null) {
            getSupportActionBar().setTitle(pref.getTitle());
        }
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        final FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
            return true;
        }
        finish();
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        PermissionsManager.get(this).onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
