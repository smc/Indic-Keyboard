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
import android.content.res.Resources

import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen

import com.android.inputmethod.latin.R

import kotlin.math.roundToInt

/** Add and return a titled, icon-space-free [PreferenceCategory] to this screen. */
fun PreferenceScreen.addCategory(context: Context, titleRes: Int): PreferenceCategory =
    PreferenceCategory(context).also {
        it.setTitle(titleRes)
        it.isIconSpaceReserved = false
        addPreference(it)
    }

/** Position-aware Material 3 card background so a run of rows reads as one rounded surface. */
internal fun cardBackground(top: Boolean, bottom: Boolean): Int = when {
    top && bottom -> R.drawable.pref_card_single
    top -> R.drawable.pref_card_top
    bottom -> R.drawable.pref_card_bottom
    else -> R.drawable.pref_card_middle
}

internal fun Resources.dpToPx(value: Int): Int = (value * displayMetrics.density).roundToInt()
