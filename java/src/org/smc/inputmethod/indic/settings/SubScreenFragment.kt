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

import android.app.backup.BackupManager
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView

import com.android.inputmethod.compat.PreferenceManagerCompat

abstract class SubScreenFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {

    private var sharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null

    protected val sharedPreferences: SharedPreferences
        get() = PreferenceManagerCompat.getDeviceSharedPreferences(activity)

    protected fun setPreferenceEnabled(prefKey: String, enabled: Boolean) {
        preferenceScreen.findPreference<Preference>(prefKey)?.isEnabled = enabled
    }

    protected fun removePreference(prefKey: String) {
        val screen = preferenceScreen
        screen.findPreference<Preference>(prefKey)?.let { screen.removePreference(it) }
    }

    protected fun <T : Preference> requirePreference(key: String): T = findPreference(key)!!

    protected fun setActionBarTitle(title: CharSequence?) {
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
    }

    /** Enable the vibration/sound sub-settings only while their master toggle is on. */
    protected fun refreshEnablingsOfKeypressSoundAndVibrationSettings() {
        val prefs = sharedPreferences
        val res = resources
        setPreferenceEnabled(
            Settings.PREF_VIBRATION_DURATION_SETTINGS, Settings.readVibrationEnabled(prefs, res)
        )
        setPreferenceEnabled(
            Settings.PREF_KEYPRESS_SOUND_VOLUME, Settings.readKeypressSoundEnabled(prefs, res)
        )
    }

    override fun addPreferencesFromResource(preferencesResId: Int) {
        super.addPreferencesFromResource(preferencesResId)
        val screen = preferenceScreen
        TwoStatePreferenceHelper.replaceCheckBoxPreferencesBySwitchPreferences(screen)
        // Inner screens have no leading icons; don't reserve the icon column so text starts flush.
        removeUnusedIconSpace(screen)
    }

    private fun removeUnusedIconSpace(group: PreferenceGroup) {
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index)
            if (preference.icon == null) {
                preference.isIconSpaceReserved = false
            }
            if (preference is PreferenceGroup) {
                removeUnusedIconSpace(preference)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            preferenceManager.setStorageDeviceProtected()
        }
        // Subclasses override this, call super first, then add their preferences resource.
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> =
        CardedPreferenceGroupAdapter(preferenceScreen)

    override fun onCreateRecyclerView(
        inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?
    ): RecyclerView {
        val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
        recyclerView.addItemDecoration(CardedPreferenceGroupAdapter.CardDivider(requireActivity()))
        return recyclerView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listener = OnSharedPreferenceChangeListener { prefs, key ->
            val context = activity
            if (context == null || preferenceScreen == null) {
                Log.w(javaClass.simpleName, "onSharedPreferenceChanged called before activity starts.")
                return@OnSharedPreferenceChangeListener
            }
            BackupManager(context).dataChanged()
            onSharedPreferenceChanged(prefs, key)
        }
        sharedPreferenceChangeListener = listener
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDestroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        // This method may be overridden by an extended class.
    }

    @Suppress("DEPRECATION")
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (parentFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return
        }
        if (preference is SeekBarDialogPreference) {
            val fragment = SeekBarDialogPreferenceFragment.newInstance(preference.key)
            fragment.setTargetFragment(this, 0)
            fragment.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
            return
        }
        super.onDisplayPreferenceDialog(preference)
    }

    companion object {
        private const val DIALOG_FRAGMENT_TAG =
            "org.smc.inputmethod.indic.settings.SubScreenFragment.DIALOG"
    }
}
