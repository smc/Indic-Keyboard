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

import androidx.core.content.edit

/**
 * Wires a [SeekBarDialogPreference] to its backing preference. Every seek-bar preference shares the
 * same write skeleton — store the value (as an int, or as a value/100 fraction) and clear it to
 * reset to default — so only the read/format/feedback behaviour is supplied per call site.
 *
 * @param storeAsFraction when true the seek-bar value is a percentage stored as value/100 as a
 *   float; when false it is stored verbatim as an int.
 */
fun SeekBarDialogPreference.bindValueProxy(
    prefs: SharedPreferences,
    storeAsFraction: Boolean = false,
    read: (key: String) -> Int,
    readDefault: (key: String) -> Int,
    text: (value: Int) -> String,
    feedback: (value: Int) -> Unit = {}
) {
    setInterface(object : SeekBarDialogPreference.ValueProxy {
        override fun readValue(key: String): Int = read(key)
        override fun readDefaultValue(key: String): Int = readDefault(key)
        override fun writeValue(value: Int, key: String) {
            prefs.edit { if (storeAsFraction) putFloat(key, value / 100f) else putInt(key, value) }
        }
        override fun writeDefaultValue(key: String) {
            prefs.edit { remove(key) }
        }
        override fun getValueText(value: Int): String = text(value)
        override fun feedbackValue(value: Int) = feedback(value)
    })
}
