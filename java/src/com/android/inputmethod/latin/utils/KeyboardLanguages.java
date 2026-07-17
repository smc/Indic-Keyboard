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

package com.android.inputmethod.latin.utils;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class KeyboardLanguages {
    private static final String TRANSLITERATION_METHOD = "TransliterationMethod";

    private static volatile Map<String, String> sGlyphByLanguage;

    /** Glyph from keyboard_languages.xml for a bare language code ("ml"), or null if unknown.
     *  Unlike {@link #getLanguages}, safe for the IME path: one lightweight parse, then cached. */
    @Nullable
    public static String glyphForLanguage(final Context context, final String langCode) {
        if (langCode == null || langCode.isEmpty()) {
            return null;
        }
        Map<String, String> glyphs = sGlyphByLanguage;
        if (glyphs == null) {
            glyphs = new HashMap<>();
            final XmlResourceParser parser = context.getResources().getXml(
                    R.xml.keyboard_languages);
            try {
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && "language".equals(parser.getName())) {
                        final String locale = parser.getAttributeValue(null, "locale");
                        final String glyph = parser.getAttributeValue(null, "glyph");
                        if (locale != null && glyph != null) {
                            glyphs.put(locale.split("_")[0], glyph);
                        }
                    }
                }
            } catch (final XmlPullParserException | IOException e) {
                Log.w("KeyboardLanguages", "Failed to parse glyphs", e);
            } finally {
                parser.close();
            }
            sGlyphByLanguage = glyphs;
        }
        return glyphs.get(langCode);
    }

    public static final class Layout {
        public final InputMethodSubtype mSubtype;
        public final String mName;

        Layout(final InputMethodSubtype subtype, final String name) {
            mSubtype = subtype;
            mName = name;
        }
    }

    public static final class Language {
        public final String mLocale;
        public final String mEnglishName;
        public final String mAutonym;
        public final String mGlyph;
        public final List<Layout> mLayouts;

        Language(final String locale, final String englishName, final String autonym,
                final String glyph, final List<Layout> layouts) {
            mLocale = locale;
            mEnglishName = englishName;
            mAutonym = autonym;
            mGlyph = glyph;
            mLayouts = Collections.unmodifiableList(layouts);
        }
    }

    private static final class DisplayNames {
        final String mEnglish; // "English · Layout"
        final String mNative;  // "Autonym · Layout"

        DisplayNames(final String english, final String nativeName) {
            mEnglish = english;
            mNative = nativeName;
        }
    }

    private static Map<String, DisplayNames> sDisplayNameCache;

    private KeyboardLanguages() {
        // This utility class is not publicly instantiable.
    }

    @Nonnull
    public static String getDisplayName(final Context context, final InputMethodSubtype subtype) {
        final DisplayNames names = displayNames(context, subtype);
        return (names != null) ? names.mEnglish
                : SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype);
    }

    @Nonnull
    public static String getNativeDisplayName(final Context context,
            final InputMethodSubtype subtype) {
        final DisplayNames names = displayNames(context, subtype);
        return (names != null) ? names.mNative
                : SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype);
    }

    private static DisplayNames displayNames(final Context context,
            final InputMethodSubtype subtype) {
        if (sDisplayNameCache == null) {
            final Map<String, DisplayNames> cache = new HashMap<>();
            for (final Language language : getLanguages(context)) {
                for (final Layout layout : language.mLayouts) {
                    cache.put(SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype),
                            new DisplayNames(language.mEnglishName + " · " + layout.mName,
                                    language.mAutonym + " · " + layout.mName));
                }
            }
            sDisplayNameCache = cache;
        }
        return sDisplayNameCache.get(SubtypeLocaleUtils.getSubtypeKey(subtype));
    }

    @Nonnull
    public static List<Language> getLanguages(final Context context) {
        SubtypeLocaleUtils.init(context);
        RichInputMethodManager.init(context);
        final Map<String, List<InputMethodSubtype>> layoutsByLocale = groupSubtypesByLocale();

        final List<Language> languages = new ArrayList<>();
        final XmlResourceParser parser = context.getResources().getXml(R.xml.keyboard_languages);
        try {
            List<InputMethodSubtype> localeSubtypes = null;
            String locale = null, englishName = null, autonym = null, glyph = null;
            List<Layout> layouts = null;
            final Set<InputMethodSubtype> matched = new HashSet<>();
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "language".equals(parser.getName())) {
                    locale = parser.getAttributeValue(null, "locale");
                    englishName = parser.getAttributeValue(null, "englishName");
                    autonym = parser.getAttributeValue(null, "autonym");
                    glyph = parser.getAttributeValue(null, "glyph");
                    localeSubtypes = layoutsByLocale.get(locale);
                    layouts = new ArrayList<>();
                    matched.clear();
                } else if (event == XmlPullParser.START_TAG && "layout".equals(parser.getName())
                        && localeSubtypes != null) {
                    final InputMethodSubtype subtype = findSubtype(localeSubtypes,
                            parser.getAttributeValue(null, "layoutSet"),
                            parser.getAttributeValue(null, "translit"));
                    if (subtype != null) {
                        layouts.add(new Layout(subtype, parser.getAttributeValue(null, "name")));
                        matched.add(subtype);
                    }
                } else if (event == XmlPullParser.END_TAG && "language".equals(parser.getName())
                        && localeSubtypes != null) {
                    // Any subtype not named in the resource still gets listed, as a safety net.
                    for (final InputMethodSubtype subtype : localeSubtypes) {
                        if (!matched.contains(subtype)) {
                            layouts.add(new Layout(subtype,
                                    SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(subtype)));
                        }
                    }
                    if (!layouts.isEmpty()) {
                        languages.add(new Language(locale, englishName, autonym, glyph, layouts));
                    }
                    localeSubtypes = null;
                }
            }
        } catch (final XmlPullParserException | IOException e) {
            throw new RuntimeException("Failed to parse keyboard_languages.xml", e);
        } finally {
            parser.close();
        }
        return languages;
    }

    private static InputMethodSubtype findSubtype(final List<InputMethodSubtype> subtypes,
            final String layoutSet, final String translit) {
        for (final InputMethodSubtype subtype : subtypes) {
            if (SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype).equals(layoutSet)
                    && TextUtils.equals(subtype.getExtraValueOf(TRANSLITERATION_METHOD), translit)) {
                return subtype;
            }
        }
        return null;
    }

    private static Map<String, List<InputMethodSubtype>> groupSubtypesByLocale() {
        final InputMethodInfo imi =
                RichInputMethodManager.getInstance().getInputMethodInfoOfThisIme();
        final Map<String, List<InputMethodSubtype>> byLocale = new HashMap<>();
        final int count = imi.getSubtypeCount();
        for (int i = 0; i < count; i++) {
            final InputMethodSubtype subtype = imi.getSubtypeAt(i);
            final String locale = subtype.getLocale();
            List<InputMethodSubtype> layouts = byLocale.get(locale);
            if (layouts == null) {
                layouts = new ArrayList<>();
                byLocale.put(locale, layouts);
            }
            layouts.add(subtype);
        }
        return byLocale;
    }
}
