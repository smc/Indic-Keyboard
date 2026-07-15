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

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.provider.Settings.Secure
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup

import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.FeedbackUtils

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            preferenceManager.setStorageDeviceProtected()
        }
        addPreferencesFromResource(R.xml.prefs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> =
        CardedPreferenceGroupAdapter(preferenceScreen)

    override fun onCreateRecyclerView(
        inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?
    ): RecyclerView {
        val recyclerView = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
        recyclerView.addItemDecoration(CardedPreferenceGroupAdapter.CardDivider(requireActivity()))
        recyclerView.isVerticalScrollBarEnabled = false
        return recyclerView
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (FeedbackUtils.isHelpAndFeedbackFormSupported()) {
            menu.add(NO_MENU_GROUP, MENU_HELP_AND_FEEDBACK, MENU_HELP_AND_FEEDBACK, R.string.help_and_feedback)
        }
        val aboutResId = FeedbackUtils.getAboutKeyboardTitleResId()
        if (aboutResId != 0) {
            menu.add(NO_MENU_GROUP, MENU_ABOUT, MENU_ABOUT, aboutResId)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val activity = requireActivity()
        if (!isUserSetupComplete(activity)) {
            // If setup is not complete, it's not safe to launch Help or other activities because
            // they might go to the Play Store. See b/19866981.
            return true
        }
        when (item.itemId) {
            MENU_HELP_AND_FEEDBACK -> {
                FeedbackUtils.showHelpAndFeedbackForm(activity)
                return true
            }
            MENU_ABOUT -> {
                val aboutIntent = FeedbackUtils.getAboutKeyboardIntent(activity)
                if (aboutIntent != null) {
                    startActivity(aboutIntent)
                    return true
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private val NO_MENU_GROUP = Menu.NONE
        private val MENU_ABOUT = Menu.FIRST
        private val MENU_HELP_AND_FEEDBACK = Menu.FIRST + 1

        private fun isUserSetupComplete(activity: Activity): Boolean =
            Secure.getInt(activity.contentResolver, "user_setup_complete", 0) != 0
    }
}
