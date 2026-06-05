/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.compat;

import android.os.Build;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.RichInputMethodSubtype;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.LocaleUtils;

import java.util.Locale;

import javax.annotation.Nonnull;

public final class InputMethodSubtypeCompatUtils {
    private InputMethodSubtypeCompatUtils() {
        // This utility class is not publicly instantiable.
    }

    @Nonnull
    public static InputMethodSubtype newInputMethodSubtype(int nameId, int iconId, String locale,
            String mode, String extraValue, boolean isAuxiliary,
            boolean overridesImplicitlyEnabledSubtype, int id) {
        return new InputMethodSubtype(nameId, iconId, locale, mode, extraValue, isAuxiliary,
                overridesImplicitlyEnabledSubtype, id);
    }

    public static boolean isAsciiCapable(final RichInputMethodSubtype subtype) {
        return isAsciiCapable(subtype.getRawSubtype());
    }

    public static boolean isAsciiCapable(final InputMethodSubtype subtype) {
        return subtype.isAsciiCapable()
                || subtype.containsExtraValueKey(Constants.Subtype.ExtraValue.ASCII_CAPABLE);
    }

    public static Locale getLocaleObject(final InputMethodSubtype subtype) {
        // {@link InputMethodSubtype#getLanguageTag()} is available only in Android N and later.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final String languageTag = subtype.getLanguageTag();
            if (!TextUtils.isEmpty(languageTag)) {
                return Locale.forLanguageTag(languageTag);
            }
        }
        return LocaleUtils.constructLocaleFromString(subtype.getLocale());
    }
}
