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
import android.widget.TextView

import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.ApplicationUtils

/** The About page hero: app icon, name, live version and tagline. Inflated from XML. */
class AboutHeaderPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.about_header
        isSelectable = false
        isIconSpaceReserved = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.about_version) as? TextView)?.text =
            context.getString(R.string.about_version, ApplicationUtils.getVersionName(context))
    }
}
