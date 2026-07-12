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

package org.smc.inputmethod.indic.userdictionary

import android.app.Fragment
import android.os.Bundle
import android.preference.PreferenceActivity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner

import com.android.inputmethod.latin.R

import org.smc.inputmethod.indic.userdictionary.UserDictionaryAddWordContents.LocaleRenderer
import org.smc.inputmethod.indic.userdictionary.UserDictionaryLocalePicker.LocationChangedListener

import java.util.Locale

// Caveat: this class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryAddWordFragment.java
// in order to deal with some devices that have issues with the user dictionary handling.

/**
 * Fragment to add a word/shortcut to the user dictionary. Unlike the UserDictionaryActivity, this
 * is only invoked within Settings from UserDictionarySettings.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class UserDictionaryAddWordFragment : Fragment(),
    AdapterView.OnItemSelectedListener, LocationChangedListener {

    private var mContents: UserDictionaryAddWordContents? = null
    private lateinit var mRootView: View
    private var mIsDeleting = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        activity!!.actionBar?.setTitle(R.string.edit_personal_dictionary)
        // Keep the instance so we remember mContents across configuration changes (eg rotation).
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        mRootView = inflater.inflate(R.layout.user_dictionary_add_word_fullscreen, null)
        mIsDeleting = false
        // A non-null mContents is the old value before a configuration change (eg rotation), so we
        // reuse its values. Otherwise read from the arguments.
        val contents = mContents
        mContents = if (contents == null) {
            UserDictionaryAddWordContents(mRootView, arguments!!)
        } else {
            // A word may have been added while rotating and we are now editing it, so switch the
            // contents to EDIT mode if it was in INSERT mode by using the copy constructor.
            UserDictionaryAddWordContents(mRootView, contents /* oldInstanceToBeEdited */)
        }
        activity!!.actionBar?.subtitle = UserDictionarySettingsUtils.getLocaleDisplayName(
            activity, mContents!!.getCurrentUserDictionaryLocale()
        )
        return mRootView
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, OPTIONS_MENU_ADD, 0, R.string.user_dict_settings_add_menu_title)
            .setIcon(R.drawable.ic_menu_add)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        menu.add(0, OPTIONS_MENU_DELETE, 0, R.string.user_dict_settings_delete)
            .setIcon(android.R.drawable.ic_menu_delete)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            OPTIONS_MENU_ADD -> {
                // The entry is added in onPause.
                activity!!.onBackPressed()
                return true
            }
            OPTIONS_MENU_DELETE -> {
                mContents!!.delete(activity!!)
                mIsDeleting = true
                activity!!.onBackPressed()
                return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        // We are being shown: display the word.
        updateSpinner()
    }

    private fun updateSpinner() {
        val localesList = mContents!!.getLocalesList(activity!!)
        val localeSpinner = mRootView.findViewById<Spinner>(R.id.user_dictionary_add_locale)
        val adapter = ArrayAdapter(activity!!, android.R.layout.simple_spinner_item, localesList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        localeSpinner.adapter = adapter
        localeSpinner.onItemSelectedListener = this
    }

    override fun onPause() {
        super.onPause()
        // We are being hidden: commit changes to the user dictionary, unless we were deleting.
        if (!mIsDeleting) {
            mContents!!.apply(activity!!, null)
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        val locale = parent.getItemAtPosition(pos) as LocaleRenderer
        if (locale.isMoreLanguages()) {
            val preferenceActivity = activity as PreferenceActivity
            preferenceActivity.startPreferenceFragment(UserDictionaryLocalePicker(), true)
        } else {
            mContents!!.updateLocale(locale.getLocaleString())
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Not sure we can come here, but if we do, that's the right thing to do.
        mContents!!.updateLocale(arguments?.getString(UserDictionaryAddWordContents.EXTRA_LOCALE))
    }

    // Called by the locale picker
    override fun onLocaleSelected(locale: Locale) {
        mContents!!.updateLocale(locale.toString())
        activity!!.onBackPressed()
    }

    companion object {
        private const val OPTIONS_MENU_ADD = Menu.FIRST
        private const val OPTIONS_MENU_DELETE = Menu.FIRST + 1
    }
}
