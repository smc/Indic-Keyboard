/*
 * Copyright (C) 2026 Jishnu Mohan
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
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView

import androidx.core.widget.TextViewCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.color.MaterialColors

/**
 * Groups consecutive preferences between section headers into a single rounded Material 3 "card",
 * giving each item a position-aware background (top / middle / bottom / single) so a section reads
 * as one continuous rounded surface, with category titles sitting above their card.
 */
class CardedPreferenceGroupAdapter(preferenceGroup: PreferenceGroup) :
    PreferenceGroupAdapter(preferenceGroup) {

    private val inset: Int
    private val gap: Int

    init {
        val res = preferenceGroup.context.resources
        inset = res.dpToPx(16)
        gap = res.dpToPx(10)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val item = holder.itemView
        val lp = item.layoutParams as? ViewGroup.MarginLayoutParams
            ?: RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        lp.leftMargin = inset
        lp.rightMargin = inset
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT  // reset; view holders are recycled
        val category = getItem(position) as? PreferenceCategory
        if (category != null) {
            item.background = null
            if (category.title.isNullOrEmpty()) {
                // A titleless category is just a card separator: collapse it so the gap comes
                // from the surrounding cards' own margins.
                lp.height = 0
                lp.topMargin = 0
                lp.bottomMargin = 0
            } else {
                lp.topMargin = gap
                lp.bottomMargin = 0
                styleCategoryTitle(holder)
            }
        } else {
            val top = isCardTop(position)
            val bottom = isCardBottom(position)
            item.setBackgroundResource(cardBackground(top, bottom))
            lp.topMargin = if (top) gap else 0
            lp.bottomMargin = if (bottom) gap else 0
        }
        item.layoutParams = lp
    }

    private fun isCardTop(position: Int): Boolean =
        position == 0 || getItem(position - 1) is PreferenceCategory

    private fun isCardBottom(position: Int): Boolean =
        position == itemCount - 1 || getItem(position + 1) is PreferenceCategory

    fun hasDividerBelow(position: Int): Boolean =
        getItem(position) !is PreferenceCategory && !isCardBottom(position)

    class CardDivider(context: Context) : RecyclerView.ItemDecoration() {
        private val paint = Paint()
        private val thickness = context.resources.dpToPx(1).coerceAtLeast(1)
        private val inset = context.resources.dpToPx(16)

        init {
            val outline = MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorOutlineVariant, 0xFF000000.toInt()
            )
            paint.color = (outline and 0x00FFFFFF) or 0x66000000
        }

        override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val adapter = parent.adapter as? CardedPreferenceGroupAdapter ?: return
            val left = inset.toFloat()
            val right = (parent.width - inset).toFloat()
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val position = parent.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION || !adapter.hasDividerBelow(position)) {
                    continue
                }
                val bottom = child.bottom.toFloat()
                canvas.drawRect(left, bottom - thickness, right, bottom, paint)
            }
        }
    }

    companion object {
        private fun styleCategoryTitle(holder: PreferenceViewHolder) {
            val title = holder.itemView.findViewById<TextView>(android.R.id.title) ?: return
            val tv = TypedValue()
            if (title.context.theme.resolveAttribute(
                    com.google.android.material.R.attr.textAppearanceTitleSmall, tv, true
                )
            ) {
                TextViewCompat.setTextAppearance(title, tv.resourceId)
            }
            title.setTextColor(
                MaterialColors.getColor(
                    title, androidx.appcompat.R.attr.colorPrimary, title.currentTextColor
                )
            )
        }
    }
}
