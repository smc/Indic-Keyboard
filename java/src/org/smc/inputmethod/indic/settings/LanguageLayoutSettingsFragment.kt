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

package org.smc.inputmethod.indic.settings

import android.content.Context
import android.os.Bundle
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

import com.android.inputmethod.keyboard.KeyboardLayoutSet
import com.android.inputmethod.keyboard.internal.NativeNumerals
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.RichInputMethodManager
import com.android.inputmethod.latin.common.LocaleUtils
import com.android.inputmethod.latin.utils.KeyboardLanguages
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager
import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager.Pack
import org.smc.inputmethod.indic.varnam.VarnamIndicKeyboard

import java.util.Locale

/**
 * Per-language details page: the language's layouts (toggles) plus a "Language pack" row that
 * shows download/installed state. Enabling any layout auto-downloads the language's pack.
 */
class LanguageLayoutSettingsFragment : SubScreenFragment(),
    LanguagePackDownloadManager.Listener {

    private lateinit var richImm: RichInputMethodManager
    private var englishName: String? = null
    private var langCode: String? = null

    private lateinit var packManager: LanguagePackDownloadManager
    private var packPref: Preference? = null
    private var pack: Pack? = null            // pack metadata from the index, or null until loaded
    private var downloading = false           // a download for this language is in flight
    private var pendingEnable = false         // enable happened before the index was available

    private val toggleListener = Preference.OnPreferenceChangeListener { preference, newValue ->
        val checked = newValue as Boolean
        val enabled = Settings.readEnabledSubtypeKeys(getSharedPreferences())
        if (checked) {
            enabled.add(preference.key)
        } else {
            if (enabled.size <= 1) {
                Toast.makeText(requireActivity(), R.string.language_keep_one_layout, Toast.LENGTH_SHORT)
                    .show()
                return@OnPreferenceChangeListener false
            }
            enabled.remove(preference.key)
        }
        richImm.setEnabledSubtypeKeys(enabled)
        if (checked) triggerPackDownload()
        true
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        val context = requireContext()
        RichInputMethodManager.init(context)
        richImm = RichInputMethodManager.getInstance()
        packManager = LanguagePackDownloadManager(context)

        val screen = preferenceManager.createPreferenceScreen(context)
        preferenceScreen = screen

        val locale = arguments?.getString(EXTRA_LOCALE)
        val target = KeyboardLanguages.getLanguages(context).firstOrNull { it.mLocale == locale }
            ?: return
        englishName = target.mEnglishName
        langCode = LocaleUtils.constructLocaleFromString(target.mLocale).language

        val hero = Preference(context)
        hero.layoutResource = R.layout.language_hero_preference
        hero.title = target.mAutonym
        hero.isSelectable = false
        hero.isIconSpaceReserved = false
        screen.addPreference(hero)

        addPackSection(context, screen)
        addCompanionLanguageSection(context, screen, target)

        val layoutsCategory = PreferenceCategory(context)
        layoutsCategory.setTitle(R.string.language_section_layouts)
        layoutsCategory.isIconSpaceReserved = false
        screen.addPreference(layoutsCategory)

        val enabled = Settings.readEnabledSubtypeKeys(getSharedPreferences())
        for (layout in target.mLayouts) {
            val key = SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype)
            val pref = LayoutPreviewPreference(context, layout.mSubtype)
            pref.isPersistent = false
            pref.key = key
            pref.title = layout.mName
            pref.isChecked = enabled.contains(key)
            pref.onPreferenceChangeListener = toggleListener
            layoutsCategory.addPreference(pref)
        }

        addNumeralsSection(context, screen, target)

        pack = findPack(packManager.cachedPacks())
        bindPack()
    }

    private fun addNumeralsSection(context: Context, screen: PreferenceScreen, target: Language) {
        val locale = LocaleUtils.constructLocaleFromString(target.mLocale)
        val digits = NativeNumerals.nativeDigits(locale) ?: return
        val category = PreferenceCategory(context)
        category.setTitle(R.string.language_section_numbers)
        category.isIconSpaceReserved = false
        screen.addPreference(category)

        val pref = SwitchPreferenceCompat(context)
        pref.widgetLayoutResource = R.layout.preference_material_switch
        pref.isPersistent = false
        pref.isIconSpaceReserved = false
        pref.setTitle(R.string.native_numerals)
        pref.summary = getString(
            R.string.native_numerals_summary, "${digits[0]} ${digits[1]} ${digits[2]}"
        )
        pref.isChecked = NativeNumerals.readUseNative(getSharedPreferences(), locale)
        pref.setOnPreferenceChangeListener { _, newValue ->
            getSharedPreferences().edit()
                .putBoolean(NativeNumerals.prefKey(locale.language), newValue as Boolean)
                .apply()
            KeyboardLayoutSet.onNumeralPreferenceChanged()
            true
        }
        category.addPreference(pref)
    }

    private fun addCompanionLanguageSection(
        context: Context, screen: PreferenceScreen, target: Language
    ) {
        if (!VarnamIndicKeyboard.schemes.containsKey("varnam-$langCode")) {
            return
        }
        val category = PreferenceCategory(context)
        category.setTitle(R.string.language_section_companion)
        category.isIconSpaceReserved = false
        screen.addPreference(category)

        val pref = SwitchPreferenceCompat(context)
        pref.widgetLayoutResource = R.layout.preference_material_switch
        pref.isPersistent = false
        pref.isIconSpaceReserved = false
        pref.setTitle(R.string.companion_language_suggestions)
        pref.summary = getString(R.string.companion_language_suggestions_summary, target.mAutonym)
        pref.isChecked = langCode == Settings.readCompanionLanguage(getSharedPreferences())
        pref.setOnPreferenceChangeListener { _, newValue ->
            val checked = newValue as Boolean
            getSharedPreferences().edit()
                .putString(Settings.PREF_COMPANION_LANGUAGE, if (checked) langCode else "")
                .apply()
            if (checked) triggerPackDownload()
            true
        }
        category.addPreference(pref)
    }

    private fun addPackSection(context: Context, screen: PreferenceScreen) {
        val category = PreferenceCategory(context)
        category.setTitle(R.string.language_pack_section)
        category.isIconSpaceReserved = false
        screen.addPreference(category)

        val pref = Preference(context)
        pref.isIconSpaceReserved = false
        pref.setTitle(R.string.language_pack_section)
        pref.setOnPreferenceClickListener {
            onPackClicked()
            true
        }
        packPref = pref
        category.addPreference(pref)
    }

    override fun onResume() {
        super.onResume()
        englishName?.let { name ->
            (activity as? AppCompatActivity)?.supportActionBar?.title = name
        }
        packManager.setListener(this)
        packManager.loadIndex()  // refresh availability / update status
    }

    override fun onPause() {
        packManager.setListener(null)
        super.onPause()
    }

    // ---- Pack download ----

    private fun findPack(schemes: List<Pack>): Pack? {
        val lang = langCode ?: return null
        return schemes.firstOrNull { lang == it.lang }
    }

    private fun triggerPackDownload() {
        val pack = pack
        if (pack != null) {
            if (packManager.ensureDownloaded(pack)) {
                downloading = true
                bindPack()
            }
        } else {
            // Index not cached yet — fetch it, then download once it arrives.
            pendingEnable = true
            packManager.loadIndex()
        }
    }

    private fun onPackClicked() {
        val pack = pack
        val ctx = context
        if (downloading || pack == null || ctx == null) return
        val installed = LanguagePackDownloadManager.installedVersion(ctx, langCode)
        if (installed >= 0 && pack.version <= installed) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(pack.name)
                .setMessage(R.string.language_pack_remove_confirm)
                .setPositiveButton(R.string.language_pack_remove) { _, _ ->
                    packManager.delete(langCode)
                    bindPack()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            packManager.download(pack)
            downloading = true
            bindPack()
        }
    }

    /** Render the pack row from current install state + index metadata. */
    private fun bindPack() {
        val pref = packPref ?: return
        val ctx = context ?: return
        if (downloading) return  // progress callbacks drive the summary while a download is in flight
        val pack = pack
        if (pack == null) {
            pref.setSummary(R.string.language_pack_unavailable)
            pref.icon = null
            pref.isEnabled = false
            return
        }
        pref.isEnabled = true
        val installed = LanguagePackDownloadManager.installedVersion(ctx, langCode)
        val size = formatSize(pack.size)
        when {
            installed < 0 -> {
                pref.summary = getString(R.string.language_pack_status_available, size, pack.version)
                pref.setIcon(R.drawable.ic_pack_download)
            }
            pack.version > installed -> {
                pref.summary = getString(R.string.language_pack_status_update, size, pack.version)
                pref.setIcon(R.drawable.ic_pack_update)
            }
            else -> {
                pref.summary = getString(R.string.language_pack_status_installed, installed)
                pref.setIcon(R.drawable.ic_pack_delete)
            }
        }
    }

    // ---- LanguagePackDownloadManager.Listener ----

    override fun onIndexLoaded(schemes: List<Pack>) {
        pack = findPack(schemes)
        val p = pack
        if (pendingEnable && p != null) {
            pendingEnable = false
            if (packManager.ensureDownloaded(p)) {
                downloading = true
            }
        }
        bindPack()
    }

    override fun onProgress(lang: String, percent: Int) {
        val pref = packPref
        if (!matches(lang) || pref == null) return
        downloading = true
        if (percent == LanguagePackDownloadManager.INSTALLING) {
            pref.setSummary(R.string.language_pack_installing)
        } else {
            pref.summary = getString(R.string.language_pack_downloading, percent)
        }
    }

    override fun onInstalled(lang: String) {
        if (!matches(lang)) return
        downloading = false
        bindPack()
    }

    override fun onError(lang: String?, message: String?) {
        if (lang != null && !matches(lang)) return
        downloading = false
        packPref?.setSummary(R.string.language_pack_download_error)
    }

    private fun matches(lang: String?): Boolean = langCode != null && langCode == lang

    companion object {
        const val EXTRA_LOCALE = "locale"

        private fun formatSize(bytes: Long): String =
            if (bytes < 1024 * 1024) {
                String.format(Locale.getDefault(), "%d KB", Math.max(1, bytes / 1024))
            } else {
                String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
            }
    }
}
