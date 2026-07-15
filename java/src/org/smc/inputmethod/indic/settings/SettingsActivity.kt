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

import android.os.Bundle
import android.view.View

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.permissions.PermissionsManager
import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    ActivityCompat.OnRequestPermissionsResultCallback {

    private var defaultTitle: CharSequence? = null

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        setContentView(R.layout.settings_activity)
        val toolbar = findViewById<MaterialToolbar>(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        defaultTitle = title

        supportFragmentManager.addOnBackStackChangedListener { updateTitle() }

        if (savedState == null) {
            val fragmentName = intent.getStringExtra(EXTRA_SHOW_FRAGMENT)
            if (isTwoPane) {
                // Master (category list) stays in the left pane; details open in the right pane.
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_list_container, SettingsFragment())
                    .commit()
                if (fragmentName != null) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.settings_container, instantiate(fragmentName, null))
                        .commit()
                }
            } else {
                val fragment = fragmentName?.let { instantiate(it, null) } ?: SettingsFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, fragment)
                    .commit()
            }
        }
    }

    private val isTwoPane: Boolean
        get() = findViewById<View>(R.id.settings_list_container) != null

    private fun instantiate(name: String, args: Bundle?): Fragment {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, name)
        if (args != null) {
            args.classLoader = fragment.javaClass.classLoader
            fragment.arguments = args
        }
        return fragment
    }

    private fun updateTitle() {
        val actionBar = supportActionBar ?: return
        if (supportFragmentManager.backStackEntryCount == 0) {
            actionBar.title = defaultTitle
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat, pref: Preference
    ): Boolean {
        val fragment = instantiate(pref.fragment!!, pref.extras)
        // Two-pane: picking a category from the list pane resets the detail; drilling deeper
        // within a category (caller is a detail fragment) stacks so Back returns one level.
        val isCategoryPick = isTwoPane && caller is SettingsFragment
        if (isCategoryPick) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, fragment)
                .commit()
        } else {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, fragment)
                .addToBackStack(null)
                .commit()
        }
        supportActionBar?.let { bar -> pref.title?.let { bar.title = it } }
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            supportFragmentManager.fragments.filterIsInstance<SettingsFragment>()
                .forEach { it.refreshSetupBanner() }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val fm = supportFragmentManager
        if (fm.backStackEntryCount > 0) {
            fm.popBackStack()
            return true
        }
        finish()
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        PermissionsManager.get(this)
            .onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        // Kept compatible with the framework PreferenceActivity extra so external deep links and
        // the system "App info" entry continue to open a specific fragment.
        const val EXTRA_SHOW_FRAGMENT = ":android:show_fragment"

        const val EXTRA_ENTRY_KEY = "entry"
        const val EXTRA_ENTRY_VALUE_APP_ICON = "app_icon"
        const val EXTRA_ENTRY_VALUE_NOTICE_DIALOG = "important_notice"
        const val EXTRA_ENTRY_VALUE_SYSTEM_SETTINGS = "system_settings"
    }
}
