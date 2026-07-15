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

import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Bundle

import androidx.core.content.getSystemService
import androidx.preference.Preference

import com.android.inputmethod.latin.AudioAndHapticFeedbackManager
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.SystemBroadcastReceiver

import org.smc.inputmethod.indic.setup.SetupWizardActivity

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

        if (!Settings.isInternal(sharedPreferences)) {
            removePreference(Settings.SCREEN_DEBUG)
        }
        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATION_DURATION_SETTINGS)
        }

        setupKeypressVibrationDurationSettings()
        setupKeypressSoundVolumeSettings()
        setupKeyLongpressTimeoutSettings()
        refreshEnablingsOfKeypressSoundAndVibrationSettings()

        findPreference<Preference>(Settings.PREF_RERUN_SETUP)?.setOnPreferenceClickListener {
            startActivity(
                Intent(requireContext(), SetupWizardActivity::class.java)
                    .putExtra(SetupWizardActivity.EXTRA_FORCE_SETUP, true)
            )
            true
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == Settings.PREF_SHOW_SETUP_WIZARD_ICON) {
            SystemBroadcastReceiver.toggleAppIcon(activity)
        }
        refreshEnablingsOfKeypressSoundAndVibrationSettings()
    }

    private fun setupKeypressVibrationDurationSettings() {
        val pref = findPreference<SeekBarDialogPreference>(
            Settings.PREF_VIBRATION_DURATION_SETTINGS
        ) ?: return
        val prefs = sharedPreferences
        val res = resources
        pref.bindValueProxy(
            prefs,
            read = { Settings.readKeypressVibrationDuration(prefs, res) },
            readDefault = { Settings.readDefaultKeypressVibrationDuration(res) },
            text = {
                if (it < 0) res.getString(R.string.settings_system_default)
                else res.getString(R.string.abbreviation_unit_milliseconds, it)
            },
            feedback = { AudioAndHapticFeedbackManager.getInstance().vibrate(it.toLong()) }
        )
    }

    private fun setupKeypressSoundVolumeSettings() {
        val pref = findPreference<SeekBarDialogPreference>(
            Settings.PREF_KEYPRESS_SOUND_VOLUME
        ) ?: return
        val prefs = sharedPreferences
        val res = resources
        val am = requireActivity().getSystemService<AudioManager>()!!
        pref.bindValueProxy(
            prefs,
            storeAsFraction = true,
            read = { (Settings.readKeypressSoundVolume(prefs, res) * 100f).toInt() },
            readDefault = { (Settings.readDefaultKeypressSoundVolume(res) * 100f).toInt() },
            text = { if (it < 0) res.getString(R.string.settings_system_default) else it.toString() },
            feedback = { am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, it / 100f) }
        )
    }

    private fun setupKeyLongpressTimeoutSettings() {
        val prefs = sharedPreferences
        val res = resources
        val pref = findPreference<SeekBarDialogPreference>(
            Settings.PREF_KEY_LONGPRESS_TIMEOUT
        ) ?: return
        pref.bindValueProxy(
            prefs,
            read = { Settings.readKeyLongpressTimeout(prefs, res) },
            readDefault = { Settings.readDefaultKeyLongpressTimeout(res) },
            text = { res.getString(R.string.abbreviation_unit_milliseconds, it) }
        )
    }
}
