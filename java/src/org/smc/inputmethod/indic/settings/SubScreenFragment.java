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

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.inputmethod.compat.PreferenceManagerCompat;

public abstract class SubScreenFragment extends PreferenceFragmentCompat
        implements OnSharedPreferenceChangeListener {
    private static final String DIALOG_FRAGMENT_TAG =
            "org.smc.inputmethod.indic.settings.SubScreenFragment.DIALOG";

    private OnSharedPreferenceChangeListener mSharedPreferenceChangeListener;

    static void setPreferenceEnabled(final String prefKey, final boolean enabled,
            final PreferenceScreen screen) {
        final Preference preference = screen.findPreference(prefKey);
        if (preference != null) {
            preference.setEnabled(enabled);
        }
    }

    static void removePreference(final String prefKey, final PreferenceScreen screen) {
        final Preference preference = screen.findPreference(prefKey);
        if (preference != null) {
            screen.removePreference(preference);
        }
    }

    static void updateListPreferenceSummaryToCurrentValue(final String prefKey,
            final PreferenceScreen screen) {
        final ListPreference listPreference = (ListPreference) screen.findPreference(prefKey);
        if (listPreference == null) {
            return;
        }
        final CharSequence entries[] = listPreference.getEntries();
        final int entryIndex = listPreference.findIndexOfValue(listPreference.getValue());
        listPreference.setSummary(entryIndex < 0 ? null : entries[entryIndex]);
    }

    final void setPreferenceEnabled(final String prefKey, final boolean enabled) {
        setPreferenceEnabled(prefKey, enabled, getPreferenceScreen());
    }

    final void removePreference(final String prefKey) {
        removePreference(prefKey, getPreferenceScreen());
    }

    final void updateListPreferenceSummaryToCurrentValue(final String prefKey) {
        updateListPreferenceSummaryToCurrentValue(prefKey, getPreferenceScreen());
    }

    final SharedPreferences getSharedPreferences() {
        return PreferenceManagerCompat.getDeviceSharedPreferences(getActivity());
    }

    /**
     * Gets the application name to display on the UI.
     */
    final String getApplicationName() {
        final Context context = getActivity();
        final Resources res = getResources();
        final int applicationLabelRes = context.getApplicationInfo().labelRes;
        return res.getString(applicationLabelRes);
    }

    @Override
    public void addPreferencesFromResource(final int preferencesResId) {
        super.addPreferencesFromResource(preferencesResId);
        TwoStatePreferenceHelper.replaceCheckBoxPreferencesBySwitchPreferences(
                getPreferenceScreen());
        // Inner screens have no leading icons; don't reserve the icon column so text starts flush.
        removeUnusedIconSpace(getPreferenceScreen());
    }

    private static void removeUnusedIconSpace(final PreferenceGroup group) {
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            final Preference preference = group.getPreference(index);
            if (preference.getIcon() == null) {
                preference.setIconSpaceReserved(false);
            }
            if (preference instanceof PreferenceGroup) {
                removeUnusedIconSpace((PreferenceGroup) preference);
            }
        }
    }

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getPreferenceManager().setStorageDeviceProtected();
        }
        // Subclasses override this, call super first, then add their preferences resource.
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(final PreferenceScreen preferenceScreen) {
        return new CardedPreferenceGroupAdapter(preferenceScreen);
    }

    @Override
    public RecyclerView onCreateRecyclerView(final LayoutInflater inflater, final ViewGroup parent,
            final Bundle savedInstanceState) {
        final RecyclerView recyclerView =
                super.onCreateRecyclerView(inflater, parent, savedInstanceState);
        recyclerView.addItemDecoration(new CardedPreferenceGroupAdapter.CardDivider(getActivity()));
        return recyclerView;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
                final SubScreenFragment fragment = SubScreenFragment.this;
                final Context context = fragment.getActivity();
                if (context == null || fragment.getPreferenceScreen() == null) {
                    final String tag = fragment.getClass().getSimpleName();
                    Log.w(tag, "onSharedPreferenceChanged called before activity starts.");
                    return;
                }
                new BackupManager(context).dataChanged();
                fragment.onSharedPreferenceChanged(prefs, key);
            }
        };
        getSharedPreferences().registerOnSharedPreferenceChangeListener(
                mSharedPreferenceChangeListener);
    }

    @Override
    public void onDestroy() {
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                mSharedPreferenceChangeListener);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        // This method may be overridden by an extended class.
    }

    @Override
    public void onDisplayPreferenceDialog(final Preference preference) {
        if (getParentFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }
        if (preference instanceof SeekBarDialogPreference) {
            final SeekBarDialogPreferenceFragment fragment =
                    SeekBarDialogPreferenceFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), DIALOG_FRAGMENT_TAG);
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }
}
