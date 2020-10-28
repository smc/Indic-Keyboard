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

    private Context mContext;
    private HashMap<String, String> dict;

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
                        getEmojiFromCodepoints(entry.getValue()),
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

    private void makeDict() {
        // TODO move strings-emoji-description.xml to a RAW file and read direct
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
     * @param codepoints Codepoints separated by underscore
     * @return
     */
    public static String getEmojiFromCodepoints(String codepoints) {
        StringBuilder result = new StringBuilder();
        for (String codepoint: codepoints.split("_")) {
            result.append(StringUtils.newSingleCodePointString(Integer.parseInt(codepoint, 16)));
        }
        return result.toString();
    }
}