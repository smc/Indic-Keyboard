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

package org.smc.inputmethod.indic.settings

import android.content.Context
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.widget.TextView

import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

import com.android.inputmethod.latin.R

open class FlatPreference(context: Context, attrs: AttributeSet?) :
    Preference(context, attrs), FlatRow {

    init {
        isIconSpaceReserved = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val text = holder.findViewById(R.id.colophon_text) as? TextView
            ?: holder.itemView as? TextView
        text?.text = title
        if (title is Spanned) {
            text?.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
