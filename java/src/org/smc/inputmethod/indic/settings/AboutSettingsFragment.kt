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
import android.net.Uri
import android.os.Bundle

import androidx.annotation.StringRes

import com.android.inputmethod.latin.R

/** "About" sub screen: app header, website/source links and the open-source licenses. */
class AboutSettingsFragment : SubScreenFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        addPreferencesFromResource(R.xml.prefs_screen_about)
        openUrlOnClick("about_website", R.string.about_website_url)
        openUrlOnClick("about_source", R.string.about_source_url)
        openUrlOnClick("about_copyright", R.string.about_copyright_url)
        openUrlOnClick("about_logo_credit", R.string.about_logo_credit_url)
        openUrlOnClick("about_contributors", R.string.about_contributors_url)
    }

    override fun onResume() {
        super.onResume()
        setActionBarTitle(getString(R.string.settings_screen_about))
    }

    private fun openUrlOnClick(key: String, @StringRes urlRes: Int) {
        findPreference<androidx.preference.Preference>(key)?.setOnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(urlRes))))
            true
        }
    }
}
