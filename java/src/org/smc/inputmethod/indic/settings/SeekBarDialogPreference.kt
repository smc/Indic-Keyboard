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

import android.content.Context
import android.util.AttributeSet

import androidx.preference.DialogPreference

import com.android.inputmethod.latin.R

class SeekBarDialogPreference(context: Context, attrs: AttributeSet?) :
    DialogPreference(context, attrs) {

    interface ValueProxy {
        fun readValue(key: String): Int
        fun readDefaultValue(key: String): Int
        fun writeValue(value: Int, key: String)
        fun writeDefaultValue(key: String)
        fun getValueText(value: Int): String
        fun feedbackValue(value: Int)
    }

    val maxValue: Int
    val minValue: Int
    val stepValue: Int

    var valueProxy: ValueProxy? = null
        private set

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SeekBarDialogPreference, 0, 0)
        maxValue = a.getInt(R.styleable.SeekBarDialogPreference_maxValue, 0)
        minValue = a.getInt(R.styleable.SeekBarDialogPreference_minValue, 0)
        stepValue = a.getInt(R.styleable.SeekBarDialogPreference_stepValue, 0)
        a.recycle()
        dialogLayoutResource = R.layout.seek_bar_dialog
    }

    fun setInterface(proxy: ValueProxy) {
        valueProxy = proxy
        summary = proxy.getValueText(proxy.readValue(key))
    }
}
