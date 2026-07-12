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
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Bundle

import com.android.inputmethod.latin.AudioAndHapticFeedbackManager
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.SystemBroadcastReceiver

/**
 * "Advanced" settings sub screen: key-popup dismiss delay, keypress vibration duration, keypress
 * sound volume, show-app-icon, improve-keyboard and the debug settings entry.
 */
class AdvancedSettingsFragment : SubScreenFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        addPreferencesFromResource(R.xml.prefs_screen_advanced)

        // When launched from the Settings app while the IME is not running, some singletons may
        // not have been initialized yet. See LatinIME.onCreate().
        AudioAndHapticFeedbackManager.init(requireContext())

        if (!Settings.isInternal(getSharedPreferences())) {
            removePreference(Settings.SCREEN_DEBUG)
        }
        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATION_DURATION_SETTINGS)
        }

        setupKeypressVibrationDurationSettings()
        setupKeypressSoundVolumeSettings()
        setupKeyLongpressTimeoutSettings()
        refreshEnablingsOfKeypressSoundAndVibrationSettings()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == Settings.PREF_SHOW_SETUP_WIZARD_ICON) {
            SystemBroadcastReceiver.toggleAppIcon(activity)
        }
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

    private fun setupKeypressVibrationDurationSettings() {
        val pref = findPreference<SeekBarDialogPreference>(
            Settings.PREF_VIBRATION_DURATION_SETTINGS
        ) ?: return
        val prefs = getSharedPreferences()
        val res = resources
        pref.setInterface(object : SeekBarDialogPreference.ValueProxy {
            override fun writeValue(value: Int, key: String) {
                prefs.edit().putInt(key, value).apply()
            }

            override fun writeDefaultValue(key: String) {
                prefs.edit().remove(key).apply()
            }

            override fun readValue(key: String): Int =
                Settings.readKeypressVibrationDuration(prefs, res)

            override fun readDefaultValue(key: String): Int =
                Settings.readDefaultKeypressVibrationDuration(res)

            override fun feedbackValue(value: Int) {
                AudioAndHapticFeedbackManager.getInstance().vibrate(value.toLong())
            }

            override fun getValueText(value: Int): String =
                if (value < 0) res.getString(R.string.settings_system_default)
                else res.getString(R.string.abbreviation_unit_milliseconds, value)
        })
    }

    private fun setupKeypressSoundVolumeSettings() {
        val pref = findPreference<SeekBarDialogPreference>(
            Settings.PREF_KEYPRESS_SOUND_VOLUME
        ) ?: return
        val prefs = getSharedPreferences()
        val res = resources
        val am = requireActivity().getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
                percentageFromValue(Settings.readKeypressSoundVolume(prefs, res))

            override fun readDefaultValue(key: String): Int =
                percentageFromValue(Settings.readDefaultKeypressSoundVolume(res))

            override fun getValueText(value: Int): String =
                if (value < 0) res.getString(R.string.settings_system_default) else value.toString()

            override fun feedbackValue(value: Int) {
                am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, valueFromPercentage(value))
            }
        })
    }

    private fun setupKeyLongpressTimeoutSettings() {
        val prefs = getSharedPreferences()
        val res = resources
        val pref = findPreference<SeekBarDialogPreference>(
            Settings.PREF_KEY_LONGPRESS_TIMEOUT
        ) ?: return
        pref.setInterface(object : SeekBarDialogPreference.ValueProxy {
            override fun writeValue(value: Int, key: String) {
                prefs.edit().putInt(key, value).apply()
            }

            override fun writeDefaultValue(key: String) {
                prefs.edit().remove(key).apply()
            }

            override fun readValue(key: String): Int = Settings.readKeyLongpressTimeout(prefs, res)

            override fun readDefaultValue(key: String): Int =
                Settings.readDefaultKeyLongpressTimeout(res)

            override fun getValueText(value: Int): String =
                res.getString(R.string.abbreviation_unit_milliseconds, value)

            override fun feedbackValue(value: Int) {}
        })
    }
}
