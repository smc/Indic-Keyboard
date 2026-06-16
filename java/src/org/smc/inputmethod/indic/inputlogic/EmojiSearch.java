/**
 * Emoji search
 * Copyright (C) 2020, Subin Siby <mail@subinsb.com>
 *               2026, Jishnu Mohan
 * Licensed under the Apache License, Version 2.0
 */

package org.smc.inputmethod.indic.inputlogic;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.BinaryDictionary;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.NgramContext;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.ComposedData;
import com.android.inputmethod.latin.common.InputPointers;

import org.smc.inputmethod.indic.settings.SettingsValuesForSuggestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class EmojiSearch {
    private static String TAG = "EmojiSearch";

    private Context mContext;
    private volatile BinaryDictionary mDict;

    public EmojiSearch(Context context) {
        mContext = context;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                openSearchDict();
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private void openSearchDict() {
        try {
            final AssetFileDescriptor afd =
                    mContext.getResources().openRawResourceFd(R.raw.emoji_search);
            if (afd == null) {
                Log.e(TAG, "emoji_search.dict is compressed / not found");
                return;
            }
            final String sourceDir = mContext.getApplicationInfo().sourceDir;
            final long offset = afd.getStartOffset();
            final long length = afd.getLength();
            afd.close();
            final BinaryDictionary d = new BinaryDictionary(sourceDir, offset, length,
                    false /* useFullEditDistance */, Locale.ENGLISH, "emoji",
                    false /* isUpdatable */);
            if (d.isValidDictionary()) {
                mDict = d;
            } else {
                Log.e(TAG, "emoji_search.dict invalid");
                d.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "openSearchDict failed", e);
        }
    }

    public ArrayList<SuggestedWords.SuggestedWordInfo> search(String query) {
        final ArrayList<SuggestedWordInfo> out = new ArrayList<>();
        final BinaryDictionary d = mDict;
        if (d == null) {
            return out;
        }
        final Keyboard kb = KeyboardSwitcher.getInstance().getKeyboard();
        if (kb == null) {
            return out;
        }
        final long proximity = kb.getProximityInfo().getNativeProximityInfo();
        // Query each whitespace-separated token; emoji must match every token (AND), ranked by the
        // summed dictionary score.
        LinkedHashMap<String, Integer> scores = null;
        for (final String token : query.toLowerCase().trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            final HashMap<String, Integer> tokenScores = queryToken(d, proximity, token);
            if (scores == null) {
                scores = new LinkedHashMap<>(tokenScores);
            } else {
                scores.keySet().retainAll(tokenScores.keySet());
                for (final Map.Entry<String, Integer> e : scores.entrySet()) {
                    e.setValue(e.getValue() + tokenScores.get(e.getKey()));
                }
            }
            if (scores.isEmpty()) {
                return out;
            }
        }
        if (scores == null) {
            return out;
        }
        final ArrayList<Map.Entry<String, Integer>> ranked = new ArrayList<>(scores.entrySet());
        Collections.sort(ranked, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return Integer.compare(b.getValue(), a.getValue());
            }
        });
        for (final Map.Entry<String, Integer> e : ranked) {
            out.add(new SuggestedWordInfo(e.getKey(), "" /* prevWordsContext */, e.getValue(),
                    SuggestedWordInfo.KIND_COMPLETION, Dictionary.DICTIONARY_RESUMED,
                    SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE));
        }
        return out;
    }

    private HashMap<String, Integer> queryToken(final BinaryDictionary d, final long proximity,
            final String token) {
        final HashMap<String, Integer> map = new HashMap<>();
        final ComposedData composed =
                new ComposedData(new InputPointers(48), false /* isBatchMode */, token);
        final ArrayList<SuggestedWordInfo> results = d.getSuggestions(composed,
                NgramContext.EMPTY_PREV_WORDS_INFO, proximity,
                new SettingsValuesForSuggestion(false), 0 /* sessionId */, 1.0f /* weightForLocale */,
                new float[] { -1.0f });
        if (results == null) {
            return map;
        }
        for (final SuggestedWordInfo info : results) {
            if (!info.isKindOf(SuggestedWordInfo.KIND_SHORTCUT)) {
                continue;
            }
            final Integer prev = map.get(info.mWord);
            if (prev == null || info.mScore > prev) {
                map.put(info.mWord, info.mScore);
            }
        }
        return map;
    }
}
