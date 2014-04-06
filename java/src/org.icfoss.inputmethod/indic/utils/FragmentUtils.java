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

package org.icfoss.inputmethod.indic.utils;

import org.icfoss.inputmethod.dictionarypack.DictionarySettingsFragment;
import org.icfoss.inputmethod.indic.about.AboutPreferences;
import org.icfoss.inputmethod.indic.settings.AdditionalSubtypeSettings;
import org.icfoss.inputmethod.indic.settings.DebugSettings;
import org.icfoss.inputmethod.indic.settings.SettingsFragment;
import org.icfoss.inputmethod.indic.spellcheck.SpellCheckerSettingsFragment;
import org.icfoss.inputmethod.indic.userdictionary.UserDictionaryAddWordFragment;
import org.icfoss.inputmethod.indic.userdictionary.UserDictionaryList;
import org.icfoss.inputmethod.indic.userdictionary.UserDictionaryLocalePicker;
import org.icfoss.inputmethod.indic.userdictionary.UserDictionarySettings;
import org.icfoss.inputmethod.research.FeedbackFragment;

import java.util.HashSet;

public class FragmentUtils {
    private static final HashSet<String> sLatinImeFragments = new HashSet<String>();
    static {
        sLatinImeFragments.add(DictionarySettingsFragment.class.getName());
        sLatinImeFragments.add(AboutPreferences.class.getName());
        sLatinImeFragments.add(AdditionalSubtypeSettings.class.getName());
        sLatinImeFragments.add(DebugSettings.class.getName());
        sLatinImeFragments.add(SettingsFragment.class.getName());
        sLatinImeFragments.add(SpellCheckerSettingsFragment.class.getName());
        sLatinImeFragments.add(UserDictionaryAddWordFragment.class.getName());
        sLatinImeFragments.add(UserDictionaryList.class.getName());
        sLatinImeFragments.add(UserDictionaryLocalePicker.class.getName());
        sLatinImeFragments.add(UserDictionarySettings.class.getName());
        sLatinImeFragments.add(FeedbackFragment.class.getName());
    }

    public static boolean isValidFragment(String fragmentName) {
        return sLatinImeFragments.contains(fragmentName);
    }
}
