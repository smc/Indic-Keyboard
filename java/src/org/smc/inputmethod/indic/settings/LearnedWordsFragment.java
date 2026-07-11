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

package org.smc.inputmethod.indic.settings;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.common.LocaleUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.smc.inputmethod.indic.personalization.PersonalizationHelper;
import org.smc.inputmethod.indic.personalization.UserHistoryDictionary;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Lists the words the keyboard learned from typing (the user history dictionaries), one
 * language at a time via filter chips, with search, per-word delete and a delete-all action.
 */
public final class LearnedWordsFragment extends Fragment {
    private static final String DICT_FILE_PREFIX = "UserHistoryDictionary.";
    private static final String DICT_FILE_SUFFIX = ".dict";
    private static final int MENU_DELETE_ALL = Menu.FIRST;

    private ChipGroup mLanguages;
    private EditText mSearch;
    private TextView mEmpty;
    private RecyclerView mList;
    private WordsAdapter mAdapter;

    private Locale mSelectedLocale;
    private final ArrayList<String> mWords = new ArrayList<>();

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        final View root = inflater.inflate(R.layout.fragment_learned_words, container, false);
        mLanguages = root.findViewById(R.id.learned_words_languages);
        mSearch = root.findViewById(R.id.learned_words_search);
        mEmpty = root.findViewById(R.id.learned_words_empty);
        mList = root.findViewById(R.id.learned_words_list);
        mList.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new WordsAdapter();
        mList.setAdapter(mAdapter);
        mSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count,
                    final int after) {}

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before,
                    final int count) {}

            @Override
            public void afterTextChanged(final Editable s) {
                mAdapter.filter(s.toString());
            }
        });
        buildLanguageChips();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.privacy_learned_typing);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, MENU_DELETE_ALL, Menu.NONE, R.string.learned_words_delete_all)
                .setIcon(R.drawable.ic_settings_delete)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() != MENU_DELETE_ALL) {
            return super.onOptionsItemSelected(item);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.privacy_learned_typing)
                .setMessage(R.string.privacy_learned_typing_confirm)
                .setPositiveButton(R.string.privacy_delete, (dialog, which) -> {
                    PersonalizationHelper.removeAllUserHistoryDictionaries(requireContext());
                    buildLanguageChips();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return true;
    }

    // The user history dictionary lives in memory between flushes, so the enabled keyboard
    // languages are the source of truth; on-disk dictionaries cover languages since disabled.
    private ArrayList<Locale> findLocalesWithHistory() {
        final LinkedHashMap<String, Locale> locales = new LinkedHashMap<>();
        RichInputMethodManager.init(requireContext());
        for (final InputMethodSubtype subtype : RichInputMethodManager.getInstance()
                .getMyEnabledInputMethodSubtypeList(true /* allowsImplicitlySelectedSubtypes */)) {
            final Locale locale = LocaleUtils.constructLocaleFromString(subtype.getLocale());
            locales.put(locale.toString(), locale);
        }
        final File[] dictFiles = requireContext().getFilesDir().listFiles((dir, name) ->
                name.startsWith(DICT_FILE_PREFIX) && name.endsWith(DICT_FILE_SUFFIX));
        if (dictFiles != null) {
            for (final File file : dictFiles) {
                final String localeString = file.getName().substring(DICT_FILE_PREFIX.length(),
                        file.getName().length() - DICT_FILE_SUFFIX.length());
                locales.put(localeString, LocaleUtils.constructLocaleFromString(localeString));
            }
        }
        final ArrayList<Locale> sorted = new ArrayList<>(locales.values());
        Collections.sort(sorted, (a, b) -> a.toString().compareTo(b.toString()));
        return sorted;
    }

    private void buildLanguageChips() {
        final Context context = requireContext();
        final ArrayList<Locale> locales = findLocalesWithHistory();
        mLanguages.removeAllViews();
        mSelectedLocale = null;
        if (locales.isEmpty()) {
            showWords(new ArrayList<>());
            return;
        }
        for (final Locale locale : locales) {
            final Chip chip = new Chip(context);
            chip.setText(locale.getDisplayName(locale));
            chip.setCheckable(true);
            chip.setTag(locale);
            mLanguages.addView(chip);
        }
        mLanguages.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            final Chip chip = group.findViewById(checkedIds.get(0));
            loadWords((Locale) chip.getTag());
        });
        ((Chip) mLanguages.getChildAt(0)).setChecked(true);
    }

    private void loadWords(final Locale locale) {
        mSelectedLocale = locale;
        final UserHistoryDictionary dictionary = PersonalizationHelper.getUserHistoryDictionary(
                requireContext(), locale, null /* account */);
        dictionary.getAllWordsAsync(words -> {
            if (!isAdded() || !locale.equals(mSelectedLocale)) {
                return;
            }
            final Collator collator = Collator.getInstance(locale);
            Collections.sort(words, collator);
            showWords(words);
        });
    }

    private void showWords(final ArrayList<String> words) {
        mWords.clear();
        mWords.addAll(words);
        mAdapter.filter(mSearch.getText().toString());
    }

    private void updateEmptyState() {
        final boolean empty = mAdapter.getItemCount() == 0;
        mEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void deleteWord(final String word) {
        if (mSelectedLocale == null) {
            return;
        }
        PersonalizationHelper.getUserHistoryDictionary(requireContext(), mSelectedLocale,
                null /* account */).removeUnigramEntryDynamically(word);
        mWords.remove(word);
        mAdapter.filter(mSearch.getText().toString());
    }

    private final class WordsAdapter extends RecyclerView.Adapter<WordViewHolder> {
        private final ArrayList<String> mFiltered = new ArrayList<>();

        void filter(final String query) {
            mFiltered.clear();
            if (TextUtils.isEmpty(query)) {
                mFiltered.addAll(mWords);
            } else {
                final Locale locale = (mSelectedLocale != null) ? mSelectedLocale
                        : Locale.getDefault();
                final String needle = query.toLowerCase(locale);
                for (final String word : mWords) {
                    if (word.toLowerCase(locale).contains(needle)) {
                        mFiltered.add(word);
                    }
                }
            }
            notifyDataSetChanged();
            updateEmptyState();
        }

        @Override
        public WordViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            final View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.learned_word_item, parent, false);
            return new WordViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final WordViewHolder holder, final int position) {
            final String word = mFiltered.get(position);
            holder.mWord.setText(word);
            final boolean top = position == 0;
            final boolean bottom = position == getItemCount() - 1;
            holder.itemView.setBackgroundResource(cardBackground(top, bottom));
            holder.mDelete.setOnClickListener(v -> deleteWord(word));
        }

        @Override
        public int getItemCount() {
            return mFiltered.size();
        }
    }

    private static int cardBackground(final boolean top, final boolean bottom) {
        if (top && bottom) return R.drawable.pref_card_single;
        if (top) return R.drawable.pref_card_top;
        if (bottom) return R.drawable.pref_card_bottom;
        return R.drawable.pref_card_middle;
    }

    private static final class WordViewHolder extends RecyclerView.ViewHolder {
        final TextView mWord;
        final View mDelete;

        WordViewHolder(final View itemView) {
            super(itemView);
            mWord = itemView.findViewById(R.id.learned_word);
            mDelete = itemView.findViewById(R.id.learned_word_delete);
        }
    }
}
