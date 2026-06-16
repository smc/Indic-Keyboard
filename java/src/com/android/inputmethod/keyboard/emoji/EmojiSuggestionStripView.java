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

package com.android.inputmethod.keyboard.emoji;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.Constants;

/**
 * Horizontal, scrollable strip of emoji search results. Kept separate from the text
 * {@link org.smc.inputmethod.indic.suggestions.SuggestionStripView}
 */
public final class EmojiSuggestionStripView extends HorizontalScrollView {
    public interface OnEmojiClickListener {
        void onEmojiClicked(SuggestedWordInfo info);
    }

    private final LinearLayout mStrip;
    private final float mEmojiSize;
    private final int mCellWidth;
    private final int mCellBackgroundResId;
    private OnEmojiClickListener mListener;
    private SuggestedWords mSuggestedWords;

    public EmojiSuggestionStripView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmojiSuggestionStripView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setHorizontalFadingEdgeEnabled(true);
        setFadingEdgeLength(
                getResources().getDimensionPixelSize(R.dimen.config_emoji_suggestions_fade_length));

        final Resources res = getResources();
        mStrip = new LinearLayout(context);
        mStrip.setOrientation(LinearLayout.HORIZONTAL);
        mStrip.setGravity(Gravity.CENTER_VERTICAL);
        mStrip.setPaddingRelative(
                res.getDimensionPixelSize(R.dimen.config_emoji_suggestions_start_padding), 0, 0, 0);
        addView(mStrip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        mEmojiSize = res.getDimension(R.dimen.config_emoji_suggestions_text_size);
        mCellWidth = res.getDimensionPixelSize(R.dimen.config_emoji_suggestions_cell_width);
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        mCellBackgroundResId = value.resourceId;
    }

    public void setSuggestions(final SuggestedWords suggestedWords,
            final OnEmojiClickListener listener) {
        mSuggestedWords = suggestedWords;
        mListener = listener;
        mStrip.removeAllViews();
        scrollTo(0, 0);
        final int count = (suggestedWords == null) ? 0 : suggestedWords.size();
        for (int i = 0; i < count; i++) {
            mStrip.addView(buildCell(suggestedWords.getLabel(i), i));
        }
    }

    public void clear() {
        mStrip.removeAllViews();
        mSuggestedWords = null;
    }

    private TextView buildCell(final String emoji, final int index) {
        final TextView cell = new TextView(getContext());
        cell.setText(emoji);
        cell.setContentDescription(emoji);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_PX, mEmojiSize);
        cell.setGravity(Gravity.CENTER);
        cell.setMinWidth(mCellWidth);
        cell.setClickable(true);
        cell.setBackgroundResource(mCellBackgroundResId);
        cell.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        cell.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                        Constants.CODE_UNSPECIFIED, EmojiSuggestionStripView.this);
                if (mListener != null && mSuggestedWords != null
                        && index < mSuggestedWords.size()) {
                    mListener.onEmojiClicked(mSuggestedWords.getInfo(index));
                }
            }
        });
        return cell;
    }
}
