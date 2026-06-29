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
import com.android.inputmethod.latin.common.LocaleUtils;
import com.android.inputmethod.latin.utils.KeyboardLanguages;
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language;
import com.android.inputmethod.latin.utils.KeyboardLanguages.Layout;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.smc.inputmethod.indic.varnam.LanguagePackDownloadManager;
import org.smc.inputmethod.indic.varnam.LanguagePackDownloadManager.Scheme;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Per-language details page: the language's layouts (toggles) plus a "Language pack" row that
 * shows download/installed state. Enabling any layout auto-downloads the language's pack.
 */
public final class LanguageLayoutSettingsFragment extends SubScreenFragment
        implements LanguagePackDownloadManager.Listener {
    public static final String EXTRA_LOCALE = "locale";

    private RichInputMethodManager mRichImm;
    private String mEnglishName;
    private String mLangCode;

    private LanguagePackDownloadManager mPackManager;
    private Preference mPackPref;
    private Scheme mScheme;          // pack metadata from the index, or null until loaded
    private boolean mDownloading;    // a download for this language is in flight
    private boolean mPendingEnable;  // enable happened before the index was available

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
            if (checked) {
                triggerPackDownload();
            }
            return true;
        }
    };

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        final Context context = getActivity();
        RichInputMethodManager.init(context);
        mRichImm = RichInputMethodManager.getInstance();
        mPackManager = new LanguagePackDownloadManager(context);

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
        mLangCode = LocaleUtils.constructLocaleFromString(target.mLocale).getLanguage();

        final Preference hero = new Preference(context);
        hero.setLayoutResource(R.layout.language_hero_preference);
        hero.setTitle(target.mAutonym);
        hero.setSelectable(false);
        hero.setIconSpaceReserved(false);
        screen.addPreference(hero);

        addPackSection(context, screen);

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

        mScheme = findScheme(mPackManager.cachedSchemes());
        bindPack();
    }

    private void addPackSection(final Context context, final PreferenceScreen screen) {
        final PreferenceCategory category = new PreferenceCategory(context);
        category.setTitle(R.string.language_pack_section);
        category.setIconSpaceReserved(false);
        screen.addPreference(category);

        mPackPref = new Preference(context);
        mPackPref.setIconSpaceReserved(false);
        mPackPref.setTitle(R.string.language_pack_section);
        mPackPref.setOnPreferenceClickListener(p -> {
            onPackClicked();
            return true;
        });
        category.addPreference(mPackPref);
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
        if (mPackManager != null) {
            mPackManager.setListener(this);
            mPackManager.loadIndex();  // refresh availability / update status
        }
    }

    @Override
    public void onPause() {
        if (mPackManager != null) {
            mPackManager.setListener(null);
        }
        super.onPause();
    }

    // ---- Pack download ----

    private Scheme findScheme(final List<Scheme> schemes) {
        if (mLangCode == null) return null;
        for (final Scheme s : schemes) {
            if (mLangCode.equals(s.lang)) return s;
        }
        return null;
    }

    private void triggerPackDownload() {
        if (mScheme != null) {
            if (mPackManager.ensureDownloaded(mScheme)) {
                mDownloading = true;
                bindPack();
            }
        } else {
            // Index not cached yet — fetch it, then download once it arrives.
            mPendingEnable = true;
            mPackManager.loadIndex();
        }
    }

    private void onPackClicked() {
        if (mDownloading || mScheme == null || getContext() == null) {
            return;
        }
        final int installed = LanguagePackDownloadManager.installedVersion(getContext(), mLangCode);
        if (installed >= 0 && mScheme.version <= installed) {
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(mScheme.name)
                    .setMessage(R.string.varnam_delete_confirm)
                    .setPositiveButton(R.string.varnam_delete, (d, w) -> {
                        mPackManager.delete(mLangCode);
                        bindPack();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            mPackManager.download(mScheme);
            mDownloading = true;
            bindPack();
        }
    }

    /** Render the pack row from current install state + index metadata. */
    private void bindPack() {
        if (mPackPref == null || getContext() == null) {
            return;
        }
        if (mDownloading) {
            return;  // progress callbacks drive the summary while a download is in flight
        }
        if (mScheme == null) {
            mPackPref.setSummary(R.string.language_pack_unavailable);
            mPackPref.setIcon(null);
            mPackPref.setEnabled(false);
            return;
        }
        mPackPref.setEnabled(true);
        final int installed = LanguagePackDownloadManager.installedVersion(getContext(), mLangCode);
        final String size = formatSize(mScheme.size);
        if (installed < 0) {
            mPackPref.setSummary(getString(R.string.varnam_status_available, size, mScheme.version));
            mPackPref.setIcon(R.drawable.ic_varnam_download);
        } else if (mScheme.version > installed) {
            mPackPref.setSummary(getString(R.string.varnam_status_update, size));
            mPackPref.setIcon(R.drawable.ic_varnam_update);
        } else {
            mPackPref.setSummary(getString(R.string.varnam_status_installed, installed));
            mPackPref.setIcon(R.drawable.ic_varnam_delete);
        }
    }

    private static String formatSize(final long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%d KB", Math.max(1, bytes / 1024));
        }
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ---- LanguagePackDownloadManager.Listener ----

    @Override
    public void onIndexLoaded(final List<Scheme> schemes) {
        mScheme = findScheme(schemes);
        if (mPendingEnable && mScheme != null) {
            mPendingEnable = false;
            if (mPackManager.ensureDownloaded(mScheme)) {
                mDownloading = true;
            }
        }
        bindPack();
    }

    @Override
    public void onProgress(final String lang, final int percent) {
        if (!matches(lang) || mPackPref == null) return;
        mDownloading = true;
        if (percent == LanguagePackDownloadManager.INSTALLING) {
            mPackPref.setSummary(R.string.varnam_installing);
        } else {
            mPackPref.setSummary(getString(R.string.varnam_downloading, percent));
        }
    }

    @Override
    public void onInstalled(final String lang) {
        if (!matches(lang)) return;
        mDownloading = false;
        bindPack();
    }

    @Override
    public void onError(final String lang, final String message) {
        if (lang != null && !matches(lang)) return;
        mDownloading = false;
        if (mPackPref != null) {
            mPackPref.setSummary(R.string.varnam_download_error_generic);
        }
    }

    private boolean matches(final String lang) {
        return mLangCode != null && mLangCode.equals(lang);
    }
}
