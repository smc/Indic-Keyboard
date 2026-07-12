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
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.ImageView

import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat

import com.android.inputmethod.keyboard.KeyboardPreviewView
import com.android.inputmethod.keyboard.KeyboardTheme
import com.android.inputmethod.latin.R

/**
 * A layout-enable switch row that expands on tap to show a live preview of the layout in the
 * user's current keyboard theme. Only the switch itself toggles the layout.
 */
class LayoutPreviewPreference(
    context: Context,
    private val subtype: InputMethodSubtype
) : SwitchPreferenceCompat(context) {

    private var expanded = false

    init {
        layoutResource = R.layout.layout_preview_preference
        widgetLayoutResource = R.layout.preference_material_switch
    }

    override fun onClick() {
        expanded = !expanded
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // The switch handles its own taps (the library listener is attached in
        // super.onBindViewHolder); the widget layout ships non-clickable for rows where the
        // whole row toggles.
        holder.findViewById(androidx.preference.R.id.switchWidget).apply {
            isClickable = true
            isFocusable = true
        }
        holder.itemView.findViewById<ImageView>(R.id.layout_expand_indicator).rotation =
            if (expanded) 180f else 0f
        val previewHolder = holder.itemView.findViewById<FrameLayout>(R.id.layout_preview_holder)
        previewHolder.visibility = if (expanded) View.VISIBLE else View.GONE
        previewHolder.clipToOutline = true
        if (expanded && previewHolder.tag !== subtype) {
            previewHolder.removeAllViews()
            previewHolder.addView(
                KeyboardPreviewView.create(context, KeyboardTheme.getKeyboardTheme(context), subtype),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            previewHolder.tag = subtype
        }
    }
}
