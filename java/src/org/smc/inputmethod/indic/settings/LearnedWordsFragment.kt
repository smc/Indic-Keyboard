/*
 * Copyright 2026, Jishnu Mohan <jishnu7@gmail.com>
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
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.RichInputMethodManager
import com.android.inputmethod.latin.common.LocaleUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.smc.inputmethod.indic.personalization.PersonalizationHelper

import java.text.Collator
import java.util.Locale

/**
 * Lists the words the keyboard learned from typing (the user history dictionaries), one language
 * at a time via filter chips, with search, per-word delete and a delete-all action.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class LearnedWordsFragment : Fragment() {

    private lateinit var languages: ChipGroup
    private lateinit var search: EditText
    private lateinit var empty: TextView
    private lateinit var list: RecyclerView
    private lateinit var adapter: WordsAdapter

    private var selectedLocale: Locale? = null
    private val words = ArrayList<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val root = inflater.inflate(R.layout.fragment_learned_words, container, false)
        languages = root.findViewById(R.id.learned_words_languages)
        search = root.findViewById(R.id.learned_words_search)
        empty = root.findViewById(R.id.learned_words_empty)
        list = root.findViewById(R.id.learned_words_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        adapter = WordsAdapter()
        list.adapter = adapter
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                adapter.filter(s.toString())
            }
        })
        buildLanguageChips()
        return root
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.privacy_learned_typing)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.add(Menu.NONE, MENU_DELETE_ALL, Menu.NONE, R.string.learned_words_delete_all)
            .setIcon(R.drawable.ic_settings_delete)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != MENU_DELETE_ALL) {
            return super.onOptionsItemSelected(item)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.privacy_learned_typing)
            .setMessage(R.string.privacy_learned_typing_confirm)
            .setPositiveButton(R.string.privacy_delete) { _, _ ->
                PersonalizationHelper.removeAllUserHistoryDictionaries(requireContext())
                buildLanguageChips()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        return true
    }

    // The user history dictionary lives in memory between flushes, so the enabled keyboard
    // languages are the source of truth; on-disk dictionaries cover languages since disabled.
    private fun findLocalesWithHistory(): List<Locale> {
        val locales = LinkedHashMap<String, Locale>()
        RichInputMethodManager.init(requireContext())
        for (subtype in RichInputMethodManager.getInstance()
            .getMyEnabledInputMethodSubtypeList(true /* allowsImplicitlySelectedSubtypes */)) {
            val locale = LocaleUtils.constructLocaleFromString(subtype.locale)
            locales[locale.toString()] = locale
        }
        val dictFiles = requireContext().filesDir.listFiles { _, name ->
            name.startsWith(DICT_FILE_PREFIX) && name.endsWith(DICT_FILE_SUFFIX)
        }
        dictFiles?.forEach { file ->
            val localeString =
                file.name.substring(DICT_FILE_PREFIX.length, file.name.length - DICT_FILE_SUFFIX.length)
            locales[localeString] = LocaleUtils.constructLocaleFromString(localeString)
        }
        return locales.values.sortedBy { it.toString() }
    }

    private fun buildLanguageChips() {
        val context = requireContext()
        val locales = findLocalesWithHistory()
        languages.removeAllViews()
        selectedLocale = null
        if (locales.isEmpty()) {
            showWords(ArrayList())
            return
        }
        for (locale in locales) {
            val chip = Chip(context)
            chip.text = locale.getDisplayName(locale)
            chip.isCheckable = true
            chip.tag = locale
            languages.addView(chip)
        }
        languages.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedIds[0])
            loadWords(chip.tag as Locale)
        }
        (languages.getChildAt(0) as Chip).isChecked = true
    }

    private fun loadWords(locale: Locale) {
        selectedLocale = locale
        val dictionary = PersonalizationHelper.getUserHistoryDictionary(
            requireContext(), locale, null /* account */
        )
        dictionary.getAllWordsAsync { fetched ->
            if (!isAdded || locale != selectedLocale) return@getAllWordsAsync
            val collator = Collator.getInstance(locale)
            fetched.sortWith { a, b -> collator.compare(a, b) }
            showWords(fetched)
        }
    }

    private fun showWords(fetched: ArrayList<String>) {
        words.clear()
        words.addAll(fetched)
        adapter.filter(search.text.toString())
    }

    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        list.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun deleteWord(word: String) {
        val locale = selectedLocale ?: return
        PersonalizationHelper.getUserHistoryDictionary(requireContext(), locale, null /* account */)
            .removeUnigramEntryDynamically(word)
        words.remove(word)
        adapter.filter(search.text.toString())
    }

    private inner class WordsAdapter : RecyclerView.Adapter<WordViewHolder>() {
        private val filtered = ArrayList<String>()

        fun filter(query: String) {
            filtered.clear()
            if (query.isEmpty()) {
                filtered.addAll(words)
            } else {
                val locale = selectedLocale ?: Locale.getDefault()
                val needle = query.lowercase(locale)
                words.filterTo(filtered) { it.lowercase(locale).contains(needle) }
            }
            notifyDataSetChanged()
            updateEmptyState()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.learned_word_item, parent, false)
            return WordViewHolder(view)
        }

        override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
            val word = filtered[position]
            holder.wordText.text = word
            val top = position == 0
            val bottom = position == itemCount - 1
            holder.itemView.setBackgroundResource(cardBackground(top, bottom))
            holder.deleteButton.setOnClickListener { deleteWord(word) }
        }

        override fun getItemCount(): Int = filtered.size
    }

    private class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val wordText: TextView = itemView.findViewById(R.id.learned_word)
        val deleteButton: View = itemView.findViewById(R.id.learned_word_delete)
    }

    companion object {
        private const val DICT_FILE_PREFIX = "UserHistoryDictionary."
        private const val DICT_FILE_SUFFIX = ".dict"
        private val MENU_DELETE_ALL = Menu.FIRST

        private fun cardBackground(top: Boolean, bottom: Boolean): Int = when {
            top && bottom -> R.drawable.pref_card_single
            top -> R.drawable.pref_card_top
            bottom -> R.drawable.pref_card_bottom
            else -> R.drawable.pref_card_middle
        }
    }
}
