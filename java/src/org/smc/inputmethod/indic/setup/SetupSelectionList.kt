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

package org.smc.inputmethod.indic.setup

import android.app.Activity
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import com.android.inputmethod.keyboard.KeyboardPreviewView
import com.android.inputmethod.keyboard.KeyboardTheme
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language
import com.android.inputmethod.latin.utils.TextDrawable
import com.google.android.material.materialswitch.MaterialSwitch

import kotlin.math.roundToInt

/** The wizard's scrolling selection list: builds section headers, switch rows and expandable
 *  layout-preview rows inside the shared [LinearLayout]. */
internal class SetupSelectionList(
    private val activity: Activity, private val list: LinearLayout
) {
    fun clear() = list.removeAllViews()

    fun setVisible(visible: Boolean) {
        list.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun addHeader(text: CharSequence) {
        val header = activity.layoutInflater.inflate(
            R.layout.setup_selection_header, list, false
        ) as TextView
        header.text = text
        list.addView(header)
    }

    /** Inflate one selection row and add it to the list, wiring its toggle when shown. */
    fun addRow(
        rowTitle: CharSequence,
        icon: Drawable? = null,
        summary: CharSequence? = null,
        showSwitch: Boolean = true,
        checked: Boolean = false,
        onToggle: (Boolean) -> Unit = {}
    ): View {
        val row = activity.layoutInflater.inflate(R.layout.setup_selection_row, list, false)
        val iconView = row.findViewById<ImageView>(R.id.selection_icon)
        if (icon != null) iconView.setImageDrawable(icon) else iconView.visibility = View.GONE
        row.findViewById<TextView>(R.id.selection_title).text = rowTitle
        if (summary != null) {
            val summaryView = row.findViewById<TextView>(R.id.selection_summary)
            summaryView.text = summary
            summaryView.visibility = View.VISIBLE
        }
        val toggle = row.findViewById<MaterialSwitch>(R.id.selection_switch)
        if (showSwitch) {
            toggle.isChecked = checked
            row.setOnClickListener {
                val nowChecked = !toggle.isChecked
                toggle.isChecked = nowChecked
                onToggle(nowChecked)
            }
        } else {
            toggle.visibility = View.GONE
        }
        list.addView(row)
        return row
    }

    /** A layout row whose header expands on tap to show a live preview; the switch toggles it. */
    fun addLayoutRow(
        name: CharSequence,
        subtype: InputMethodSubtype,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        val row = activity.layoutInflater.inflate(R.layout.setup_layout_row, list, false)
        row.findViewById<TextView>(R.id.selection_title).text = name
        val toggle = row.findViewById<MaterialSwitch>(R.id.selection_switch)
        toggle.isChecked = checked
        toggle.setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        val chevron = row.findViewById<ImageView>(R.id.layout_expand_indicator)
        val previewHolder = row.findViewById<FrameLayout>(R.id.layout_preview_holder)
        previewHolder.clipToOutline = true
        row.findViewById<View>(R.id.layout_row_header).setOnClickListener {
            val expand = previewHolder.visibility != View.VISIBLE
            chevron.rotation = if (expand) 180f else 0f
            previewHolder.visibility = if (expand) View.VISIBLE else View.GONE
            if (expand && previewHolder.tag !== subtype) {
                previewHolder.removeAllViews()
                previewHolder.addView(
                    KeyboardPreviewView.create(
                        activity, KeyboardTheme.getKeyboardTheme(activity), subtype
                    ),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                previewHolder.tag = subtype
            }
        }
        list.addView(row)
    }

    fun glyphIcon(language: Language): Drawable {
        val value = TypedValue()
        activity.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true)
        val size = (40 * activity.resources.displayMetrics.density).roundToInt()
        return TextDrawable(language.mGlyph, value.data, 0 /* no background */, size)
    }
}
