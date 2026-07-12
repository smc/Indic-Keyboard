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
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.android.inputmethod.compat.PreferenceManagerCompat
import com.android.inputmethod.keyboard.KeyboardPreviewView
import com.android.inputmethod.keyboard.KeyboardTheme
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.RichInputMethodManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * "Keyboard theme" settings sub screen: a grid of live keyboard previews, one per theme, rendered
 * with the user's current layout, grouped by family. Tapping a card applies the theme immediately.
 */
class ThemeSettingsFragment : Fragment() {
    private var selectedThemeId = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        RichInputMethodManager.init(context)
        selectedThemeId = KeyboardTheme.getKeyboardTheme(context).mThemeId

        val grid = RecyclerView(context)
        val padding = dp(6)
        grid.setPadding(padding, padding, padding, padding)
        grid.clipToPadding = false
        val spanCount = Math.max(2, resources.configuration.screenWidthDp / 280)
        val layoutManager = GridLayoutManager(context, spanCount)
        val adapter = ThemeAdapter(context)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (adapter.isHeader(position)) spanCount else 1
        }
        grid.layoutManager = layoutManager
        grid.adapter = adapter
        return grid
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.settings_screen_theme)
    }

    private fun dp(value: Int): Int = Math.round(
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        )
    )

    private class Item(
        val header: Boolean,
        val themeId: Int,
        val label: String,
        val description: String?
    )

    private inner class ThemeAdapter(context: Context) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val items = ArrayList<Item>()
        private val subtype: InputMethodSubtype =
            RichInputMethodManager.getInstance().currentSubtype.rawSubtype

        init {
            val res = context.resources
            val names = res.getStringArray(R.array.keyboard_theme_short_names)
            val themeIds = res.getIntArray(R.array.keyboard_theme_ids)
            val groups = res.getStringArray(R.array.keyboard_theme_groups)
            val groupDescs = res.getStringArray(R.array.keyboard_theme_group_descs)
            var currentGroup: String? = null
            for (index in themeIds.indices) {
                if (groups[index] != currentGroup) {
                    currentGroup = groups[index]
                    items.add(Item(true, -1, currentGroup, groupDescs[index]))
                }
                items.add(Item(false, themeIds[index], names[index], null))
            }
        }

        fun isHeader(position: Int): Boolean = items[position].header

        override fun getItemViewType(position: Int): Int =
            if (items[position].header) VIEW_TYPE_HEADER else VIEW_TYPE_THEME

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == VIEW_TYPE_HEADER) {
                HeaderViewHolder(
                    inflater.inflate(R.layout.keyboard_theme_group_header, parent, false)
                )
            } else {
                ThemeViewHolder(inflater.inflate(R.layout.keyboard_theme_item, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (item.header) {
                (holder as HeaderViewHolder).bind(item.label, item.description)
                return
            }
            val themeHolder = holder as ThemeViewHolder
            themeHolder.bind(item.themeId, item.label, subtype, item.themeId == selectedThemeId)
            themeHolder.itemView.setOnClickListener { selectTheme(item.themeId) }
        }

        override fun getItemCount(): Int = items.size

        private fun selectTheme(themeId: Int) {
            if (themeId == selectedThemeId) return
            selectedThemeId = themeId
            KeyboardTheme.saveKeyboardThemeId(
                themeId, PreferenceManagerCompat.getDeviceSharedPreferences(requireContext())
            )
            for (index in items.indices) {
                if (!items[index].header) notifyItemChanged(index)
            }
        }
    }

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.theme_group_title)
        private val description: TextView = itemView.findViewById(R.id.theme_group_desc)

        fun bind(label: String, desc: String?) {
            title.text = label
            description.text = desc
        }
    }

    private inner class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.theme_card)
        private val previewHolder: FrameLayout = itemView.findViewById(R.id.theme_preview_holder)
        private val name: TextView = itemView.findViewById(R.id.theme_name)
        private val selectedIcon: View = itemView.findViewById(R.id.theme_selected_icon)
        private val checkedStrokeColor =
            MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary)
        private val uncheckedStrokeColor =
            MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant)
        private var boundThemeId = -1

        init {
            card.clipToOutline = true
        }

        fun bind(themeId: Int, label: String, subtype: InputMethodSubtype, selected: Boolean) {
            if (themeId != boundThemeId) {
                previewHolder.removeAllViews()
                previewHolder.addView(
                    KeyboardPreviewView.create(
                        itemView.context, KeyboardTheme.getKeyboardTheme(themeId), subtype
                    ),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                boundThemeId = themeId
            }
            name.text = label
            selectedIcon.visibility = if (selected) View.VISIBLE else View.GONE
            card.isChecked = selected
            card.strokeWidth = dp(if (selected) 2 else 1)
            card.setStrokeColor(if (selected) checkedStrokeColor else uncheckedStrokeColor)
            card.contentDescription = label
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_THEME = 1

        @JvmStatic
        fun updateKeyboardThemeSummary(pref: Preference) {
            val res = pref.context.resources
            val keyboardTheme = KeyboardTheme.getKeyboardTheme(pref.context)
            val names = res.getStringArray(R.array.keyboard_theme_names)
            val ids = res.getIntArray(R.array.keyboard_theme_ids)
            for (index in names.indices) {
                if (keyboardTheme.mThemeId == ids[index]) {
                    pref.summary = names[index]
                    return
                }
            }
        }
    }
}
