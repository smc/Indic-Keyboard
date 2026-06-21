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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.KeyboardLanguages;
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language;
import com.android.inputmethod.latin.utils.KeyboardLanguages.Layout;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;
import com.android.inputmethod.latin.utils.TextDrawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class LanguageSettingsFragment extends SubScreenFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        buildScreen();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.language_selection_title);
            }
        }
        // A language moves between the enabled and available sections after its layouts are
        // edited, so rebuild the whole screen each time we return to it.
        buildScreen();
    }

    private void buildScreen() {
        final Context context = getActivity();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);

        final Set<String> enabledKeys = Settings.readEnabledSubtypeKeys(getSharedPreferences());
        final List<Language> enabled = new ArrayList<>();
        final List<Language> available = new ArrayList<>();
        for (final Language language : KeyboardLanguages.getLanguages(context)) {
            (isEnabled(language, enabledKeys) ? enabled : available).add(language);
        }
        sortByEnglishName(enabled);
        sortByEnglishName(available);

        addSection(context, screen, R.string.language_section_enabled, enabled);
        addSection(context, screen, R.string.language_section_available, available);
    }

    private void addSection(final Context context, final PreferenceScreen screen,
            final int titleResId, final List<Language> languages) {
        if (languages.isEmpty()) {
            return;
        }
        final PreferenceCategory category = new PreferenceCategory(context);
        category.setTitle(titleResId);
        category.setIconSpaceReserved(false);
        screen.addPreference(category);
        for (final Language language : languages) {
            final Preference pref = new Preference(context);
            pref.setTitle(formatName(language));
            pref.setIcon(createIcon(context, language));
            pref.setFragment(LanguageLayoutSettingsFragment.class.getName());
            pref.getExtras().putString(
                    LanguageLayoutSettingsFragment.EXTRA_LOCALE, language.mLocale);
            category.addPreference(pref);
        }
    }

    private static boolean isEnabled(final Language language, final Set<String> enabledKeys) {
        for (final Layout layout : language.mLayouts) {
            if (enabledKeys.contains(SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype))) {
                return true;
            }
        }
        return false;
    }

    private static void sortByEnglishName(final List<Language> languages) {
        Collections.sort(languages, new Comparator<Language>() {
            @Override
            public int compare(final Language a, final Language b) {
                final boolean aEn = a.mLocale.startsWith("en");
                final boolean bEn = b.mLocale.startsWith("en");
                if (aEn != bEn) {
                    return aEn ? -1 : 1;
                }
                return a.mEnglishName.compareToIgnoreCase(b.mEnglishName);
            }
        });
    }

    private static CharSequence formatName(final Language language) {
        if (language.mEnglishName.equalsIgnoreCase(language.mAutonym)) {
            return language.mEnglishName;
        }
        return language.mEnglishName + " (" + language.mAutonym + ")";
    }

    private Drawable createIcon(final Context context, final Language language) {
        final int accent = resolveColor(context, androidx.appcompat.R.attr.colorPrimary);
        final int size = Math.round(40 * context.getResources().getDisplayMetrics().density);
        return new TextDrawable(language.mGlyph, accent, 0, size);
    }

    private static int resolveColor(final Context context, final int attr) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }
}
