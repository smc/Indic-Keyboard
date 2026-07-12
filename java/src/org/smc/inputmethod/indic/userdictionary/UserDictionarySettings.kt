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

@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package org.smc.inputmethod.indic.userdictionary

import android.app.ListFragment
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.UserDictionary
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AlphabetIndexer
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.SectionIndexer
import android.widget.SimpleCursorAdapter
import android.widget.TextView

import com.android.inputmethod.latin.R

import java.util.Locale

// Caveat: this class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionarySettings.java
// in order to deal with some devices that have issues with the user dictionary handling.
class UserDictionarySettings : ListFragment() {

    private var cursor: Cursor? = null
    private var locale: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity!!.actionBar?.setTitle(R.string.edit_personal_dictionary)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(
        R.layout.user_dictionary_preference_list_fragment, container, false
    )

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val intent = activity!!.intent
        val localeFromIntent = intent?.getStringExtra("locale")
        val localeFromArguments = arguments?.getString("locale")
        locale = localeFromArguments ?: localeFromIntent

        // WARNING: the following cursor is never closed! TODO: don't keep it in a member and close
        // all cursors properly. It comes from Activity#managedQuery (long deprecated, and which
        // FORBIDS closing the cursor). Either use a regular query and close, or a CursorLoader.
        cursor = createCursor(locale)
        val emptyView = view!!.findViewById<TextView>(android.R.id.empty)
        emptyView.setText(R.string.user_dict_settings_empty_text)

        listView.adapter = createAdapter()
        listView.isFastScrollEnabled = true
        listView.emptyView = emptyView

        setHasOptionsMenu(true)
        // Show the language as a subtitle of the action bar.
        activity!!.actionBar?.subtitle =
            UserDictionarySettingsUtils.getLocaleDisplayName(activity, locale)
    }

    override fun onResume() {
        super.onResume()
        val adapter = listView.adapter
        if (adapter is MyAdapter) {
            // Force-refresh so that add/update/delete done in UserDictionaryAddWordFragment show
            // when the user comes back to this view.
            adapter.notifyDataSetChanged()
        }
    }

    private fun createCursor(locale: String?): Cursor {
        // Locale can be: the string form of a Locale; the empty string (words valid for all
        // locales); or null (words for the current locale). Note that in the database NULL means
        // "all locales" and there should never be an empty string — a historical confusion.
        if ("" == locale) {
            // Case-insensitive sort
            return activity!!.managedQuery(
                UserDictionary.Words.CONTENT_URI, QUERY_PROJECTION, QUERY_SELECTION_ALL_LOCALES,
                null, "UPPER(" + UserDictionary.Words.WORD + ")"
            )
        }
        val queryLocale = locale ?: Locale.getDefault().toString()
        return activity!!.managedQuery(
            UserDictionary.Words.CONTENT_URI, QUERY_PROJECTION, QUERY_SELECTION,
            arrayOf(queryLocale), "UPPER(" + UserDictionary.Words.WORD + ")"
        )
    }

    private fun createAdapter(): ListAdapter =
        MyAdapter(activity, R.layout.user_dictionary_item, cursor, ADAPTER_FROM, ADAPTER_TO)

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val word = getWord(position)
        val shortcut = getShortcut(position)
        if (word != null) {
            showAddOrEditDialog(word, shortcut)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, OPTIONS_MENU_ADD, 0, R.string.user_dict_settings_add_menu_title)
            .setIcon(R.drawable.ic_menu_add)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == OPTIONS_MENU_ADD) {
            showAddOrEditDialog(null, null)
            return true
        }
        return false
    }

    /**
     * Add or edit a word. If [editingWord] is null, it's an add; otherwise, an edit.
     */
    private fun showAddOrEditDialog(editingWord: String?, editingShortcut: String?) {
        val args = Bundle()
        args.putInt(
            UserDictionaryAddWordContents.EXTRA_MODE,
            if (editingWord == null) UserDictionaryAddWordContents.MODE_INSERT
            else UserDictionaryAddWordContents.MODE_EDIT
        )
        args.putString(UserDictionaryAddWordContents.EXTRA_WORD, editingWord)
        args.putString(UserDictionaryAddWordContents.EXTRA_SHORTCUT, editingShortcut)
        args.putString(UserDictionaryAddWordContents.EXTRA_LOCALE, locale)
        val pa = activity as android.preference.PreferenceActivity
        pa.startPreferencePanel(
            UserDictionaryAddWordFragment::class.java.name, args,
            R.string.user_dict_settings_add_dialog_title, null, null, 0
        )
    }

    private fun getWord(position: Int): String? {
        val cursor = this.cursor ?: return null
        cursor.moveToPosition(position)
        // Handle a possible race condition.
        if (cursor.isAfterLast) return null
        return cursor.getString(cursor.getColumnIndexOrThrow(UserDictionary.Words.WORD))
    }

    private fun getShortcut(position: Int): String? {
        val cursor = this.cursor ?: return null
        cursor.moveToPosition(position)
        // Handle a possible race condition.
        if (cursor.isAfterLast) return null
        return cursor.getString(cursor.getColumnIndexOrThrow(UserDictionary.Words.SHORTCUT))
    }

    private class MyAdapter(
        context: Context?, layout: Int, c: Cursor?, from: Array<String>, to: IntArray
    ) : SimpleCursorAdapter(context, layout, c, from, to, 0 /* flags */), SectionIndexer {

        private var indexer: AlphabetIndexer? = null

        init {
            if (c != null) {
                val alphabet = context!!.getString(R.string.user_dict_fast_scroll_alphabet)
                val wordColIndex = c.getColumnIndexOrThrow(UserDictionary.Words.WORD)
                indexer = AlphabetIndexer(c, wordColIndex, alphabet)
            }
            viewBinder = SimpleCursorAdapter.ViewBinder { v, cur, columnIndex ->
                if (columnIndex == INDEX_SHORTCUT) {
                    val shortcut = cur.getString(INDEX_SHORTCUT)
                    if (shortcut.isNullOrEmpty()) {
                        v.visibility = View.GONE
                    } else {
                        (v as TextView).text = shortcut
                        v.visibility = View.VISIBLE
                    }
                    v.invalidate()
                    true
                } else {
                    false
                }
            }
        }

        override fun getPositionForSection(section: Int): Int =
            indexer?.getPositionForSection(section) ?: 0

        override fun getSectionForPosition(position: Int): Int =
            indexer?.getSectionForPosition(position) ?: 0

        override fun getSections(): Array<Any>? = indexer?.sections
    }

    companion object {
        private val QUERY_PROJECTION = arrayOf(
            UserDictionary.Words._ID, UserDictionary.Words.WORD, UserDictionary.Words.SHORTCUT
        )

        // The index of the shortcut in the above array.
        private const val INDEX_SHORTCUT = 2

        private val ADAPTER_FROM =
            arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.SHORTCUT)
        private val ADAPTER_TO = intArrayOf(android.R.id.text1, android.R.id.text2)

        // Either the locale is empty (word applies to all locales) or it equals our current locale.
        private val QUERY_SELECTION = UserDictionary.Words.LOCALE + "=?"
        private val QUERY_SELECTION_ALL_LOCALES = UserDictionary.Words.LOCALE + " is null"

        private val DELETE_SELECTION_WITH_SHORTCUT =
            UserDictionary.Words.WORD + "=? AND " + UserDictionary.Words.SHORTCUT + "=?"
        private val DELETE_SELECTION_WITHOUT_SHORTCUT =
            UserDictionary.Words.WORD + "=? AND " + UserDictionary.Words.SHORTCUT + " is null OR " +
                UserDictionary.Words.SHORTCUT + "=''"

        private const val OPTIONS_MENU_ADD = Menu.FIRST

        fun deleteWord(word: String?, shortcut: String?, resolver: ContentResolver) {
            if (shortcut.isNullOrEmpty()) {
                resolver.delete(
                    UserDictionary.Words.CONTENT_URI, DELETE_SELECTION_WITHOUT_SHORTCUT,
                    arrayOf(word)
                )
            } else {
                resolver.delete(
                    UserDictionary.Words.CONTENT_URI, DELETE_SELECTION_WITH_SHORTCUT,
                    arrayOf(word, shortcut)
                )
            }
        }
    }
}
