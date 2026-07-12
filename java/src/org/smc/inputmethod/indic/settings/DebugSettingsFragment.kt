/*
 * Copyright 2026, Jishnu Mohan <jishnu@gmail.com>
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
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process

import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.TwoStatePreference

import com.android.inputmethod.latin.DictionaryDumpBroadcastReceiver
import com.android.inputmethod.latin.DictionaryFacilitatorImpl
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.ApplicationUtils
import com.android.inputmethod.latin.utils.ResourceUtils

import java.util.Locale

/** "Debug mode" settings sub screen: a handful of options for debugging. */
class DebugSettingsFragment : SubScreenFragment(), Preference.OnPreferenceClickListener {

    private var serviceNeedsRestart = false
    private var debugMode: TwoStatePreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        addPreferencesFromResource(R.xml.prefs_screen_debug)

        if (!Settings.SHOULD_SHOW_LXX_SUGGESTION_UI) {
            removePreference(DebugSettings.PREF_SHOULD_SHOW_LXX_SUGGESTION_UI)
        }

        val dictDumpPreferenceGroup = findPreference<PreferenceGroup>(PREF_KEY_DUMP_DICTS)!!
        for (dictName in DictionaryFacilitatorImpl.DICT_TYPE_TO_CLASS.keys) {
            val pref = DictDumpPreference(requireActivity(), dictName)
            pref.onPreferenceClickListener = this
            dictDumpPreferenceGroup.addPreference(pref)
        }

        val res = resources
        setupKeyPreviewAnimationDuration(
            DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_DURATION,
            res.getInteger(R.integer.config_key_preview_show_up_duration)
        )
        setupKeyPreviewAnimationDuration(
            DebugSettings.PREF_KEY_PREVIEW_DISMISS_DURATION,
            res.getInteger(R.integer.config_key_preview_dismiss_duration)
        )
        val defaultShowUpStartScale =
            ResourceUtils.getFloatFromFraction(res, R.fraction.config_key_preview_show_up_start_scale)
        val defaultDismissEndScale =
            ResourceUtils.getFloatFromFraction(res, R.fraction.config_key_preview_dismiss_end_scale)
        setupKeyPreviewAnimationScale(
            DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_X_SCALE, defaultShowUpStartScale
        )
        setupKeyPreviewAnimationScale(
            DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_Y_SCALE, defaultShowUpStartScale
        )
        setupKeyPreviewAnimationScale(
            DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_X_SCALE, defaultDismissEndScale
        )
        setupKeyPreviewAnimationScale(
            DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_Y_SCALE, defaultDismissEndScale
        )

        serviceNeedsRestart = false
        debugMode = findPreference(DebugSettings.PREF_DEBUG_MODE)
        updateDebugMode()
    }

    private class DictDumpPreference(context: Context, val dictName: String) : Preference(context) {
        init {
            key = PREF_KEY_DUMP_DICT_PREFIX + dictName
            title = "Dump $dictName dictionary"
        }
    }

    override fun onPreferenceClick(pref: Preference): Boolean {
        if (pref is DictDumpPreference) {
            val intent = Intent(DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION)
            intent.putExtra(DictionaryDumpBroadcastReceiver.DICTIONARY_NAME_KEY, pref.dictName)
            requireActivity().sendBroadcast(intent)
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        if (serviceNeedsRestart) {
            Process.killProcess(Process.myPid())
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == DebugSettings.PREF_DEBUG_MODE) {
            debugMode?.let {
                it.isChecked = prefs.getBoolean(DebugSettings.PREF_DEBUG_MODE, false)
                updateDebugMode()
                serviceNeedsRestart = true
            }
            return
        }
        if (key == DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH) {
            serviceNeedsRestart = true
        }
    }

    private fun updateDebugMode() {
        val pref = debugMode ?: return
        val version = getString(R.string.version_text, ApplicationUtils.getVersionName(requireActivity()))
        if (!pref.isChecked) {
            pref.title = version
            pref.summary = null
        } else {
            pref.title = getString(R.string.prefs_debug_mode)
            pref.summary = version
        }
    }

    private fun setupKeyPreviewAnimationScale(prefKey: String, defaultValue: Float) {
        val prefs = getSharedPreferences()
        val res = resources
        val pref = findPreference<SeekBarDialogPreference>(prefKey) ?: return
        pref.setInterface(object : SeekBarDialogPreference.ValueProxy {
            private fun valueFromPercentage(percentage: Int): Float = percentage / 100.0f
            private fun percentageFromValue(floatValue: Float): Int = (floatValue * 100.0f).toInt()

            override fun writeValue(value: Int, key: String) {
                prefs.edit().putFloat(key, valueFromPercentage(value)).apply()
            }

            override fun writeDefaultValue(key: String) {
                prefs.edit().remove(key).apply()
            }

            override fun readValue(key: String): Int =
                percentageFromValue(Settings.readKeyPreviewAnimationScale(prefs, key, defaultValue))

            override fun readDefaultValue(key: String): Int = percentageFromValue(defaultValue)

            override fun getValueText(value: Int): String =
                if (value < 0) res.getString(R.string.settings_system_default)
                else String.format(Locale.ROOT, "%d%%", value)

            override fun feedbackValue(value: Int) {}
        })
    }

    private fun setupKeyPreviewAnimationDuration(prefKey: String, defaultValue: Int) {
        val prefs = getSharedPreferences()
        val res = resources
        val pref = findPreference<SeekBarDialogPreference>(prefKey) ?: return
        pref.setInterface(object : SeekBarDialogPreference.ValueProxy {
            override fun writeValue(value: Int, key: String) {
                prefs.edit().putInt(key, value).apply()
            }

            override fun writeDefaultValue(key: String) {
                prefs.edit().remove(key).apply()
            }

            override fun readValue(key: String): Int =
                Settings.readKeyPreviewAnimationDuration(prefs, key, defaultValue)

            override fun readDefaultValue(key: String): Int = defaultValue

            override fun getValueText(value: Int): String =
                res.getString(R.string.abbreviation_unit_milliseconds, value)

            override fun feedbackValue(value: Int) {}
        })
    }

    companion object {
        private const val PREF_KEY_DUMP_DICTS = "pref_key_dump_dictionaries"
        private const val PREF_KEY_DUMP_DICT_PREFIX = "pref_key_dump_dictionaries"
    }
}
