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

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.provider.UserDictionary
import android.text.TextUtils
import android.view.View
import android.widget.EditText

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.LocaleUtils

import java.util.Locale

// Caveat: this class is basically taken from
// packages/apps/Settings/src/com/android/settings/inputmethod/UserDictionaryAddWordContents.java
// in order to deal with some devices that have issues with the user dictionary handling.

/**
 * A container class factoring out the common code shared by UserDictionaryAddWordFragment and
 * UserDictionaryAddWordActivity.
 */
class UserDictionaryAddWordContents {
    private val mMode: Int // Either MODE_EDIT or MODE_INSERT
    private val mWordEditText: EditText
    private val mShortcutEditText: EditText?
    private var mLocale: String
    private val mOldWord: String?
    private val mOldShortcut: String?
    private var mSavedWord: String? = null
    private var mSavedShortcut: String? = null

    internal constructor(view: View, args: Bundle) {
        mWordEditText = view.findViewById(R.id.user_dictionary_add_word_text)
        mShortcutEditText = view.findViewById(R.id.user_dictionary_add_shortcut)
        if (!UserDictionarySettings.IS_SHORTCUT_API_SUPPORTED) {
            mShortcutEditText?.visibility = View.GONE
            view.findViewById<View>(R.id.user_dictionary_add_shortcut_label).visibility = View.GONE
        }
        val word = args.getString(EXTRA_WORD)
        if (word != null) {
            mWordEditText.setText(word)
            // Use getText in case the edit text modified the text we set (happens when it's too
            // long to be edited).
            mWordEditText.setSelection(mWordEditText.text.length)
        }
        if (UserDictionarySettings.IS_SHORTCUT_API_SUPPORTED) {
            val shortcut = args.getString(EXTRA_SHORTCUT)
            if (shortcut != null && mShortcutEditText != null) {
                mShortcutEditText.setText(shortcut)
            }
            mOldShortcut = args.getString(EXTRA_SHORTCUT)
        } else {
            mOldShortcut = null
        }
        mMode = args.getInt(EXTRA_MODE) // default return value for getInt() is 0 = MODE_EDIT
        mOldWord = args.getString(EXTRA_WORD)
        mLocale = args.getString(EXTRA_LOCALE) ?: Locale.getDefault().toString()
    }

    internal constructor(view: View, oldInstanceToBeEdited: UserDictionaryAddWordContents) {
        mWordEditText = view.findViewById(R.id.user_dictionary_add_word_text)
        mShortcutEditText = view.findViewById(R.id.user_dictionary_add_shortcut)
        mMode = MODE_EDIT
        mOldWord = oldInstanceToBeEdited.mSavedWord
        mOldShortcut = oldInstanceToBeEdited.mSavedShortcut
        // The original passed the still-null mLocale to updateLocale, i.e. the default locale.
        mLocale = Locale.getDefault().toString()
    }

    // locale may be null (default locale) or the empty string ("all locales").
    internal fun updateLocale(locale: String?) {
        mLocale = locale ?: Locale.getDefault().toString()
    }

    internal fun saveStateIntoBundle(outState: Bundle) {
        outState.putString(EXTRA_WORD, mWordEditText.text.toString())
        outState.putString(EXTRA_ORIGINAL_WORD, mOldWord)
        if (mShortcutEditText != null) {
            outState.putString(EXTRA_SHORTCUT, mShortcutEditText.text.toString())
        }
        if (mOldShortcut != null) {
            outState.putString(EXTRA_ORIGINAL_SHORTCUT, mOldShortcut)
        }
        outState.putString(EXTRA_LOCALE, mLocale)
    }

    internal fun delete(context: Context) {
        if (MODE_EDIT == mMode && !TextUtils.isEmpty(mOldWord)) {
            // Mode edit: remove the old entry.
            UserDictionarySettings.deleteWord(mOldWord, mOldShortcut, context.contentResolver)
        }
        // If we are in add mode, nothing was added, so we don't need to do anything.
    }

    internal fun apply(context: Context, outParameters: Bundle?): Int {
        if (outParameters != null) saveStateIntoBundle(outParameters)
        val resolver = context.contentResolver
        if (MODE_EDIT == mMode && !TextUtils.isEmpty(mOldWord)) {
            // Mode edit: remove the old entry.
            UserDictionarySettings.deleteWord(mOldWord, mOldShortcut, resolver)
        }
        val newWord = mWordEditText.text.toString()
        val newShortcut: String? = when {
            !UserDictionarySettings.IS_SHORTCUT_API_SUPPORTED -> null
            mShortcutEditText == null -> null
            else -> mShortcutEditText.text.toString().ifEmpty { null }
        }
        if (TextUtils.isEmpty(newWord)) {
            // If the word is somehow empty, don't insert it.
            return CODE_CANCEL
        }
        mSavedWord = newWord
        mSavedShortcut = newShortcut
        // If there is no shortcut, and the word already exists, we should not insert: either the
        // word exists with no shortcut (same thing we'd insert) or it exists with a shortcut (which
        // has priority over our word).
        if (TextUtils.isEmpty(newShortcut) && hasWord(newWord, context)) {
            return CODE_ALREADY_PRESENT
        }

        // Disallow duplicates. Remove the same word with no shortcut, and the same word with the
        // same shortcut; a same word with a different, non-empty shortcut is left alone.
        UserDictionarySettings.deleteWord(newWord, null, resolver)
        if (!TextUtils.isEmpty(newShortcut)) {
            // If newShortcut is empty we just deleted this, no need to do it again.
            UserDictionarySettings.deleteWord(newWord, newShortcut, resolver)
        }

        // We use the empty string for 'all locales' and mLocale is never null; addWord takes null
        // to mean 'all locales'.
        UserDictionary.Words.addWord(
            context, newWord, FREQUENCY_FOR_USER_DICTIONARY_ADDS, newShortcut,
            if (TextUtils.isEmpty(mLocale)) null else LocaleUtils.constructLocaleFromString(mLocale)
        )
        return CODE_WORD_ADDED
    }

    private fun hasWord(word: String, context: Context): Boolean {
        // mLocale == "" indicates an entry for all languages. mLocale is never null here (ensured
        // by updateLocale).
        val cursor = if ("" == mLocale) {
            context.contentResolver.query(
                UserDictionary.Words.CONTENT_URI, HAS_WORD_PROJECTION,
                HAS_WORD_SELECTION_ALL_LOCALES, arrayOf(word), null /* sort order */
            )
        } else {
            context.contentResolver.query(
                UserDictionary.Words.CONTENT_URI, HAS_WORD_PROJECTION,
                HAS_WORD_SELECTION_ONE_LOCALE, arrayOf(word, mLocale), null /* sort order */
            )
        }
        return try {
            cursor != null && cursor.count > 0
        } finally {
            cursor?.close()
        }
    }

    class LocaleRenderer(context: Context, private val mLocaleString: String?) {
        private val mDescription: String = when {
            mLocaleString == null -> context.getString(R.string.user_dict_settings_more_languages)
            mLocaleString.isEmpty() -> context.getString(R.string.user_dict_settings_all_languages)
            else -> LocaleUtils.constructLocaleFromString(mLocaleString).displayName
        }

        override fun toString(): String = mDescription

        fun getLocaleString(): String? = mLocaleString

        // "More languages..." is null; "All languages" is the empty string.
        fun isMoreLanguages(): Boolean = mLocaleString == null
    }

    // Helper method to get the list of locales to display for this word.
    fun getLocalesList(activity: Activity): ArrayList<LocaleRenderer> {
        val locales = UserDictionaryList.getUserDictionaryLocalesSet(activity)!!
        // Remove our locale if present, because we always put it at the top.
        locales.remove(mLocale) // mLocale may not be null
        val systemLocale = Locale.getDefault().toString()
        // The system locale should be inside. We want it at the 2nd spot.
        locales.remove(systemLocale) // system locale may not be null
        locales.remove("") // Remove the empty string if it's there
        val localesList = ArrayList<LocaleRenderer>()
        // Add the passed locale, then the system locale at the top; "all languages" at the bottom.
        addLocaleDisplayNameToList(activity, localesList, mLocale)
        if (systemLocale != mLocale) {
            addLocaleDisplayNameToList(activity, localesList, systemLocale)
        }
        for (l in locales) {
            // TODO: sort in unicode order
            addLocaleDisplayNameToList(activity, localesList, l)
        }
        if ("" != mLocale) {
            // If mLocale is "", we already inserted the "all languages" item, so don't do it again.
            addLocaleDisplayNameToList(activity, localesList, "") // meaning: all languages
        }
        localesList.add(LocaleRenderer(activity, null)) // meaning: select another locale
        return localesList
    }

    fun getCurrentUserDictionaryLocale(): String = mLocale

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_WORD = "word"
        const val EXTRA_SHORTCUT = "shortcut"
        const val EXTRA_LOCALE = "locale"
        const val EXTRA_ORIGINAL_WORD = "originalWord"
        const val EXTRA_ORIGINAL_SHORTCUT = "originalShortcut"

        const val MODE_EDIT = 0
        const val MODE_INSERT = 1

        private const val CODE_WORD_ADDED = 0
        private const val CODE_CANCEL = 1
        private const val CODE_ALREADY_PRESENT = 2

        private const val FREQUENCY_FOR_USER_DICTIONARY_ADDS = 250

        private val HAS_WORD_PROJECTION = arrayOf(UserDictionary.Words.WORD)
        private val HAS_WORD_SELECTION_ONE_LOCALE =
            UserDictionary.Words.WORD + "=? AND " + UserDictionary.Words.LOCALE + "=?"
        private val HAS_WORD_SELECTION_ALL_LOCALES =
            UserDictionary.Words.WORD + "=? AND " + UserDictionary.Words.LOCALE + " is null"

        private fun addLocaleDisplayNameToList(
            context: Context, list: ArrayList<LocaleRenderer>, locale: String?
        ) {
            if (locale != null) {
                list.add(LocaleRenderer(context, locale))
            }
        }
    }
}
