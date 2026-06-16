/**
 * Emoji search
 * Copyright (C) 2020, Subin Siby <mail@subinsb.com>
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
import com.android.inputmethod.latin.common.StringUtils;

import org.smc.inputmethod.indic.settings.SettingsValuesForSuggestion;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EmojiSearch {
    private static String TAG = "EmojiSearch";
    private static HashMap<String, String> dict;

    private Context mContext;
    private volatile BinaryDictionary mDict;

    public EmojiSearch(Context context) {
        mContext = context;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                makeDict();
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
                Log.d(TAG, "emoji_search.dict loaded ok");
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
        final String token = query.toLowerCase().trim();
        if (token.isEmpty()) {
            return out;
        }
        // Native getSuggestions requires a valid ProximityInfo handle (passing 0 crashes natively).
        final Keyboard kb = KeyboardSwitcher.getInstance().getKeyboard();
        if (kb == null) {
            return out;
        }
        final long proximity = kb.getProximityInfo().getNativeProximityInfo();
        final ComposedData composed =
                new ComposedData(new InputPointers(48), false /* isBatchMode */, token);
        final ArrayList<SuggestedWordInfo> results = d.getSuggestions(composed,
                NgramContext.EMPTY_PREV_WORDS_INFO, proximity,
                new SettingsValuesForSuggestion(false), 0 /* sessionId */, 1.0f /* weightForLocale */,
                new float[] { -1.0f });
        Log.d(TAG, "search('" + token + "') -> " + (results == null ? "null" : results.size()));
        if (results == null) {
            return out;
        }
        for (final SuggestedWordInfo info : results) {
            Log.d(TAG, "result word=[" + info.mWord + "] kind=" + info.mKindAndFlags
                    + " score=" + info.mScore);
            out.add(info);
        }
        return out;
    }

    public String getDescription(String emoji) {
        emoji = getCodepointsFromString(emoji);
        String codepoint;
        for (Map.Entry<String, String> entry : dict.entrySet()) {
            codepoint = entry.getValue();
            if (codepoint.equals(emoji)) {
                return toTitleCase(entry.getKey());
            }
        }
        return null;
    }

    private static String toTitleCase(String input) {
        StringBuilder titleCase = new StringBuilder(input.length());
        boolean nextTitleCase = true;

        for (char c : input.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                nextTitleCase = true;
            } else if (nextTitleCase) {
                c = Character.toTitleCase(c);
                nextTitleCase = false;
            }

            titleCase.append(c);
        }

        return titleCase.toString();
    }

    private void makeDict() {
        if (dict != null) return;

        // TODO move strings-emoji-description.xml to a dictionary and read direct
        Log.d(TAG, "making emoji dictionary");

        // Make emoji dictionary
        Field[] fields = R.string.class.getDeclaredFields();
        dict = new HashMap<>();

        try {
            String name, description;
            for (Field field : fields) {
                name = field.getName();
                if (
                        name.length() > 13 &&
                        name.substring(0, 13).equals("spoken_emoji_") &&
                        !name.equals("spoken_emoji_unknown")
                ) {
                    description = mContext.getString(field.getInt(field))
                            .toLowerCase()
                            .replaceAll("[^a-zA-Z0-9]","") /* remove all non alphanumeric chars */;
                    dict.put(
                            description,
                            name.substring(13) /* codepoints */
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get emoji string from codepoint
     * @param codepoints Codepoints (hexa) separated by underscore
     * @return emoji text
     */
    public static String getStringFromCodepoints(String codepoints) {
        StringBuilder result = new StringBuilder();
        for (String codepoint: codepoints.split("_")) {
            result.append(StringUtils.newSingleCodePointString(Integer.parseInt(codepoint, 16)));
        }
        return result.toString();
    }

    /**
     * Get codepoints separated by _ from a string
     * @param str String
     * @return Codepoints (hexa) separated by underscore
     */
    public static String getCodepointsFromString(String str) {
        StringBuilder codepoints = new StringBuilder();
        final int length = str.length();
        for (int offset = 0; offset < length; ) {
            final int codepoint = str.codePointAt(offset);
            final String hexa = Integer.toHexString(codepoint);

            if (!codepoints.toString().equals("")) codepoints.append("_");
            codepoints.append(hexa.toUpperCase());

            offset += Character.charCount(codepoint);
        }

        return codepoints.toString();
    }
}