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

package com.android.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.inputmethod.compat.PreferenceManagerCompat;

/**
 * The user's preferred default skin tone for emoji that support the Fitzpatrick modifiers. The
 * value is an index: 0 keeps the neutral (yellow) default, 1..5 select the light..dark tones in
 * the order they appear in an emoji's variation list. Every tone stays reachable via long-press.
 */
public final class EmojiSkinTone {
    public static final String PREF_KEY = "pref_emoji_skin_tone";

    private EmojiSkinTone() {
        // This utility class is not publicly instantiable.
    }

    /** The selected tone index (0 = neutral), clamped to a sane range. */
    public static int read(final Context context) {
        return read(PreferenceManagerCompat.getDeviceSharedPreferences(context));
    }

    public static int read(final SharedPreferences prefs) {
        try {
            final int tone = Integer.parseInt(prefs.getString(PREF_KEY, "0"));
            return (tone < 0) ? 0 : tone;
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    /**
     * The emoji labels for a skin-modifiable key's long-press menu, given its neutral base and the
     * per-tone variations. When a tone is the default, the neutral base leads the list so it stays
     * selectable; otherwise the variations are offered as-is.
     */
    public static String[] moreKeyLabels(final String base, final String[] variations,
            final int tone) {
        if (tone <= 0 || tone > variations.length) {
            return variations;
        }
        final String[] labels = new String[variations.length + 1];
        labels[0] = base;
        System.arraycopy(variations, 0, labels, 1, variations.length);
        return labels;
    }
}
