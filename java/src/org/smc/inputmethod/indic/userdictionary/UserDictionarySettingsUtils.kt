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

package org.smc.inputmethod.indic.userdictionary

import android.content.Context

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.LocaleUtils

/** Utilities for the user dictionary settings. */
object UserDictionarySettingsUtils {
    @JvmStatic
    fun getLocaleDisplayName(context: Context, localeStr: String?): String {
        if (localeStr.isNullOrEmpty()) {
            // CAVEAT: localeStr should not be null because a null locale stands for the system
            // locale in UserDictionary.Words.addWord.
            return context.resources.getString(R.string.user_dict_settings_all_languages)
        }
        val locale = LocaleUtils.constructLocaleFromString(localeStr)
        val systemLocale = context.resources.configuration.locale
        return locale.getDisplayName(systemLocale)
    }
}
