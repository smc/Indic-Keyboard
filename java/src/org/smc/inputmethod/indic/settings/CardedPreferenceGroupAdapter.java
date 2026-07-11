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

package org.smc.inputmethod.indic.settings;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.android.inputmethod.latin.R;
import com.google.android.material.color.MaterialColors;

/**
 * Groups consecutive preferences between section headers into a single rounded Material 3 "card",
 * giving each item a position-aware background (top / middle / bottom / single) so a section reads
 * as one continuous rounded surface, with category titles sitting above their card.
 */
public class CardedPreferenceGroupAdapter extends PreferenceGroupAdapter {
    private final int mInset;
    private final int mGap;

    public CardedPreferenceGroupAdapter(final PreferenceGroup preferenceGroup) {
        super(preferenceGroup);
        final Resources res = preferenceGroup.getContext().getResources();
        mInset = dp(res, 16);
        mGap = dp(res, 10);
    }

    private static int dp(final Resources res, final int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, res.getDisplayMetrics()));
    }

    @Override
    public void onBindViewHolder(final PreferenceViewHolder holder, final int position) {
        super.onBindViewHolder(holder, position);
        final View item = holder.itemView;
        final ViewGroup.LayoutParams raw = item.getLayoutParams();
        final ViewGroup.MarginLayoutParams lp = (raw instanceof ViewGroup.MarginLayoutParams)
                ? (ViewGroup.MarginLayoutParams) raw
                : new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = mInset;
        lp.rightMargin = mInset;
        final Preference pref = getItem(position);
        if (pref instanceof PreferenceCategory) {
            item.setBackground(null);
            lp.topMargin = mGap;
            lp.bottomMargin = 0;
            styleCategoryTitle(holder);
        } else {
            final boolean top = isCardTop(position);
            final boolean bottom = isCardBottom(position);
            item.setBackgroundResource(cardBackground(top, bottom));
            lp.topMargin = top ? mGap : 0;
            lp.bottomMargin = bottom ? mGap : 0;
        }
        item.setLayoutParams(lp);
    }

    private static void styleCategoryTitle(final PreferenceViewHolder holder) {
        final View titleView = holder.findViewById(android.R.id.title);
        if (!(titleView instanceof TextView)) {
            return;
        }
        final TextView title = (TextView) titleView;
        final Context context = title.getContext();
        final TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.textAppearanceTitleSmall, tv, true)) {
            TextViewCompat.setTextAppearance(title, tv.resourceId);
        }
        title.setTextColor(MaterialColors.getColor(
                title, androidx.appcompat.R.attr.colorPrimary, title.getCurrentTextColor()));
    }

    private boolean isCardTop(final int position) {
        return position == 0 || getItem(position - 1) instanceof PreferenceCategory;
    }

    private boolean isCardBottom(final int position) {
        return position == getItemCount() - 1 || getItem(position + 1) instanceof PreferenceCategory;
    }

    boolean hasDividerBelow(final int position) {
        return !(getItem(position) instanceof PreferenceCategory) && !isCardBottom(position);
    }

    public static final class CardDivider extends RecyclerView.ItemDecoration {
        private final Paint mPaint = new Paint();
        private final int mThickness;
        private final int mInset;

        public CardDivider(final Context context) {
            final Resources res = context.getResources();
            mThickness = Math.max(1, dp(res, 1));
            mInset = dp(res, 16);
            final int outline = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOutlineVariant, 0xFF000000);
            mPaint.setColor((outline & 0x00FFFFFF) | 0x66000000);
        }

        @Override
        public void onDrawOver(final Canvas canvas, final RecyclerView parent,
                final RecyclerView.State state) {
            final RecyclerView.Adapter<?> adapter = parent.getAdapter();
            if (!(adapter instanceof CardedPreferenceGroupAdapter)) {
                return;
            }
            final CardedPreferenceGroupAdapter cardAdapter =
                    (CardedPreferenceGroupAdapter) adapter;
            final int left = mInset;
            final int right = parent.getWidth() - mInset;
            for (int i = 0; i < parent.getChildCount(); i++) {
                final View child = parent.getChildAt(i);
                final int position = parent.getChildAdapterPosition(child);
                if (position == RecyclerView.NO_POSITION
                        || !cardAdapter.hasDividerBelow(position)) {
                    continue;
                }
                final int bottom = child.getBottom();
                canvas.drawRect(left, bottom - mThickness, right, bottom, mPaint);
            }
        }
    }

    private static int cardBackground(final boolean top, final boolean bottom) {
        if (top && bottom) return R.drawable.pref_card_single;
        if (top) return R.drawable.pref_card_top;
        if (bottom) return R.drawable.pref_card_bottom;
        return R.drawable.pref_card_middle;
    }
}
