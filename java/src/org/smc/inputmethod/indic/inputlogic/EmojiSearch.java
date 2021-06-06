/**
 * Emoji search
 * Copyright (C) 2020, Subin Siby <mail@subinsb.com>
 * Licensed under the Apache License, Version 2.0
 */

package org.smc.inputmethod.indic.inputlogic;

import android.content.Context;
import android.util.Log;

import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.common.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class EmojiSearch {
    private static String TAG = "EmojiSearch";
    private static HashMap<String, String> dict;

    private Context mContext;

    public EmojiSearch(Context context) {
        mContext = context;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                makeDict();
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public ArrayList<SuggestedWords.SuggestedWordInfo> search(String query) {
        query = query.toLowerCase();
        ArrayList<SuggestedWords.SuggestedWordInfo> suggestedEmojis = new ArrayList<SuggestedWords.SuggestedWordInfo>();

        String description;
        for (Map.Entry<String, String> entry : dict.entrySet()) {
            description = entry.getKey();
            if (description.contains(query)) {
                final SuggestedWords.SuggestedWordInfo emojiInfo = new SuggestedWords.SuggestedWordInfo(
                        getStringFromCodepoints(entry.getValue()),
                        "" /* prevWordsContext */,
                        description.length() - query.length(),
                        SuggestedWords.SuggestedWordInfo.KIND_COMPLETION,
                        Dictionary.DICTIONARY_RESUMED,
                        SuggestedWords.SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                        SuggestedWords.SuggestedWordInfo.NOT_A_CONFIDENCE);
                suggestedEmojis.add(emojiInfo);
            }
        }
        return suggestedEmojis;
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