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
import com.android.inputmethod.keyboard.KeyboardId;

import java.util.Locale;

/**
 * Per-language numeral-system preference. The number row and the symbols keyboard always carry
 * the two systems split between them: arabic digits on the number row and the language's own
 * on the symbols keyboard by default, swapped when the preference is on. Native digits come
 * from the generated keyboard texts table (fed by tools/make-keyboard-text), wherever the
 * language's resources put them.
 */
public final class NativeNumerals {
    private static final String PREF_PREFIX = "pref_native_numerals_";

    private static final String[] ARABIC_DIGITS =
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};

    private NativeNumerals() {
        // This utility class is not publicly instantiable.
    }

    public static String prefKey(final String language) {
        return PREF_PREFIX + language;
    }

    private static String digitTextName(final String prefix, final int index) {
        return prefix + ARABIC_DIGITS[index];
    }

    private static boolean isNativeDigit(final String text) {
        return text != null && !text.isEmpty()
                && text.codePointCount(0, text.length()) == 1
                && text.codePointAt(0) > 0x7F
                && Character.isDigit(text.codePointAt(0));
    }

    /** The language's digits ordered 1..9,0, or null when the language has none. */
    public static String[] nativeDigits(final Locale locale) {
        final String[] table = KeyboardTextsTable.getTextsTable(locale);
        final String[] digits = new String[10];
        for (int i = 0; i < 10; i++) {
            final String keyspec = KeyboardTextsTable.getText(
                    digitTextName("keyspec_symbols_", i), table);
            if (isNativeDigit(keyspec)) {
                digits[i] = keyspec;
                continue;
            }
            final String moreKeys = KeyboardTextsTable.getText(
                    digitTextName("additional_morekeys_symbols_", i), table);
            if (isNativeDigit(moreKeys)) {
                digits[i] = moreKeys;
                continue;
            }
            return null;
        }
        return digits;
    }

    public static boolean readUseNative(final SharedPreferences prefs, final Locale locale) {
        return prefs.getBoolean(prefKey(locale.getLanguage()), false);
    }

    /** Applies the numeral split for the keyboard element being built. */
    public static void apply(final Context context, final Locale locale,
            final KeyboardTextsSet textsSet, final int elementId) {
        final String[] nativeDigits = nativeDigits(locale);
        if (nativeDigits == null) {
            return;
        }
        final SharedPreferences prefs =
                PreferenceManagerCompat.getDeviceSharedPreferences(context);
        final boolean symbolsView = elementId == KeyboardId.ELEMENT_SYMBOLS
                || elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED;
        if (readUseNative(prefs, locale) != symbolsView) {
            textsSet.setNumberDigits(nativeDigits, ARABIC_DIGITS);
        } else {
            textsSet.setNumberDigits(ARABIC_DIGITS, nativeDigits);
        }
    }
}
