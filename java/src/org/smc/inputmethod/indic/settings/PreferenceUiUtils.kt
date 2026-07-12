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

import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen

/** Add and return a titled, icon-space-free [PreferenceCategory] to this screen. */
fun PreferenceScreen.addCategory(context: Context, titleRes: Int): PreferenceCategory =
    PreferenceCategory(context).also {
        it.setTitle(titleRes)
        it.isIconSpaceReserved = false
        addPreference(it)
    }
