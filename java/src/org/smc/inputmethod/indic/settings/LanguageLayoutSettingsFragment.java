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

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.utils.KeyboardLanguages;
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language;
import com.android.inputmethod.latin.utils.KeyboardLanguages.Layout;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.Set;

public final class LanguageLayoutSettingsFragment extends SubScreenFragment {
    public static final String EXTRA_LOCALE = "locale";

    private RichInputMethodManager mRichImm;
    private String mEnglishName;

    private final Preference.OnPreferenceChangeListener mToggleListener =
            new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, final Object newValue) {
            final boolean checked = (Boolean) newValue;
            final Set<String> enabled = Settings.readEnabledSubtypeKeys(getSharedPreferences());
            if (checked) {
                enabled.add(preference.getKey());
            } else {
                if (enabled.size() <= 1) {
                    Toast.makeText(getActivity(), R.string.language_keep_one_layout,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                enabled.remove(preference.getKey());
            }
            mRichImm.setEnabledSubtypeKeys(enabled);
            return true;
        }
    };

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        final Context context = getActivity();
        RichInputMethodManager.init(context);
        mRichImm = RichInputMethodManager.getInstance();

        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);

        final String locale = (getArguments() != null)
                ? getArguments().getString(EXTRA_LOCALE) : null;
        Language target = null;
        for (final Language language : KeyboardLanguages.getLanguages(context)) {
            if (language.mLocale.equals(locale)) {
                target = language;
                break;
            }
        }
        if (target == null) {
            return;
        }
        mEnglishName = target.mEnglishName;

        final Preference hero = new Preference(context);
        hero.setLayoutResource(R.layout.language_hero_preference);
        hero.setTitle(target.mAutonym);
        hero.setSelectable(false);
        hero.setIconSpaceReserved(false);
        screen.addPreference(hero);

        final PreferenceCategory layoutsCategory = new PreferenceCategory(context);
        layoutsCategory.setTitle(R.string.language_section_layouts);
        layoutsCategory.setIconSpaceReserved(false);
        screen.addPreference(layoutsCategory);

        final Set<String> enabled = Settings.readEnabledSubtypeKeys(getSharedPreferences());
        for (final Layout layout : target.mLayouts) {
            final String key = SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype);
            final SwitchPreferenceCompat pref = new SwitchPreferenceCompat(context);
            pref.setWidgetLayoutResource(R.layout.preference_material_switch);
            pref.setPersistent(false);
            pref.setIconSpaceReserved(false);
            pref.setKey(key);
            pref.setTitle(layout.mName);
            pref.setChecked(enabled.contains(key));
            pref.setOnPreferenceChangeListener(mToggleListener);
            layoutsCategory.addPreference(pref);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mEnglishName != null && getActivity() instanceof AppCompatActivity) {
            final ActionBar actionBar =
                    ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(mEnglishName);
            }
        }
    }
}
