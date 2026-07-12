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

import android.content.SharedPreferences
import android.os.Bundle

import com.android.inputmethod.latin.AudioAndHapticFeedbackManager
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.RichInputMethodManager

import java.util.Locale

/**
 * "Preferences" settings sub screen: auto-capitalization, double-space period, keypress
 * vibration/sound/popup, voice-input key and keyboard height.
 */
class PreferencesSettingsFragment : SubScreenFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        addPreferencesFromResource(R.xml.prefs_screen_preferences)

        val res = resources

        // When launched from the Settings app while the IME is not running, some singletons may
        // not have been initialized yet. See LatinIME.onCreate().
        RichInputMethodManager.init(requireContext())

        if (!res.getBoolean(R.bool.config_enable_show_voice_key_option)) {
            removePreference(Settings.PREF_VOICE_INPUT_KEY)
        }
        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATE_ON)
        }
        if (!Settings.readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
            removePreference(Settings.PREF_POPUP_ON)
        }

        refreshEnablingsOfKeypressSoundAndVibrationSettings()
        setupKeyboardHeight(Settings.PREF_KEYBOARD_HEIGHT_SCALE, SettingsValues.DEFAULT_SIZE_SCALE)
    }

    override fun onResume() {
        super.onResume()
        val voiceInputKeyOption = findPreference<androidx.preference.Preference>(Settings.PREF_VOICE_INPUT_KEY)
        if (voiceInputKeyOption != null) {
            RichInputMethodManager.getInstance().refreshSubtypeCaches()
            voiceInputKeyOption.isEnabled = VOICE_IME_ENABLED
            voiceInputKeyOption.summary =
                if (VOICE_IME_ENABLED) null else getText(R.string.voice_input_disabled_summary)
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        refreshEnablingsOfKeypressSoundAndVibrationSettings()
    }

    private fun refreshEnablingsOfKeypressSoundAndVibrationSettings() {
        val prefs = getSharedPreferences()
        val res = resources
        setPreferenceEnabled(
            Settings.PREF_VIBRATION_DURATION_SETTINGS, Settings.readVibrationEnabled(prefs, res)
        )
        setPreferenceEnabled(
            Settings.PREF_KEYPRESS_SOUND_VOLUME, Settings.readKeypressSoundEnabled(prefs, res)
        )
    }

    private fun setupKeyboardHeight(prefKey: String, defaultValue: Float) {
        val prefs = getSharedPreferences()
        val pref = findPreference<SeekBarDialogPreference>(prefKey) ?: return
        pref.setInterface(object : SeekBarDialogPreference.ValueProxy {
            private fun valueFromPercentage(percentage: Int): Float = percentage / PERCENTAGE_FLOAT
            private fun percentageFromValue(floatValue: Float): Int =
                Math.round(floatValue * PERCENTAGE_FLOAT)

            override fun writeValue(value: Int, key: String) {
                prefs.edit().putFloat(key, valueFromPercentage(value)).apply()
            }

            override fun writeDefaultValue(key: String) {
                prefs.edit().remove(key).apply()
            }

            override fun readValue(key: String): Int =
                percentageFromValue(Settings.readKeyboardHeight(prefs, defaultValue))

            override fun readDefaultValue(key: String): Int = percentageFromValue(defaultValue)

            override fun getValueText(value: Int): String = String.format(Locale.ROOT, "%d%%", value)

            override fun feedbackValue(value: Int) {}
        })
    }

    companion object {
        private const val VOICE_IME_ENABLED = true
        private const val PERCENTAGE_FLOAT = 100.0f
    }
}
