/*
 * Copyright 2026, Jishnu Mohan <jishnu@gmail.com>
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

package org.smc.inputmethod.indic.settings

import android.content.SharedPreferences
import android.os.Bundle

import com.android.inputmethod.keyboard.KeyboardLayoutSet
import com.android.inputmethod.keyboard.internal.EmojiSkinTone
import com.android.inputmethod.latin.R

/** "Emoji" settings sub screen: emoji key visibility, default skin tone and the physical-key shortcut. */
class EmojiSettingsFragment : SubScreenFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        addPreferencesFromResource(R.xml.prefs_screen_emoji)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == EmojiSkinTone.PREF_KEY) {
            KeyboardLayoutSet.onEmojiSkinTonePreferenceChanged()
        }
    }
}
