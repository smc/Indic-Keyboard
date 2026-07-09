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

package com.android.inputmethod.keyboard.clipboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.inputmethod.compat.PreferenceManagerCompat;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.utils.ResourceUtils;

import org.smc.inputmethod.indic.clipboard.ClipboardHistoryEntry;
import org.smc.inputmethod.indic.clipboard.ClipboardHistoryManager;
import org.smc.inputmethod.indic.settings.Settings;

import java.util.List;

public final class ClipboardHistoryView extends LinearLayout implements
        View.OnClickListener, View.OnTouchListener,
        ClipboardHistoryManager.OnHistoryChangedListener,
        ClipboardHistoryAdapter.OnClipboardEntryActionListener {

    public interface OnClipboardEntryClickedListener {
        void onClipboardEntryClicked(ClipboardHistoryEntry entry);
    }

    private KeyboardActionListener mKeyboardActionListener =
            KeyboardActionListener.EMPTY_LISTENER;
    private OnClipboardEntryClickedListener mOnClipboardEntryClickedListener;
    private ClipboardHistoryManager mClipboardHistoryManager;
    private ClipboardHistoryAdapter mAdapter;
    private int mTotalHeight;

    private ImageButton mBackKey;
    private ImageButton mToggleButton;
    private ImageButton mClearAllButton;
    private RecyclerView mHistoryList;
    private View mEmptyView;
    private TextView mEmptyText;
    private TextView mTurnOnButton;

    public ClipboardHistoryView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.emojiPalettesViewStyle);
    }

    public ClipboardHistoryView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
        mClipboardHistoryManager = ClipboardHistoryManager.init(context);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final Resources res = getContext().getResources();
        final int width = ResourceUtils.getDefaultKeyboardWidth(getContext())
                + getPaddingLeft() + getPaddingRight();
        final int height = (mTotalHeight > 0
                ? mTotalHeight
                : ResourceUtils.getDefaultKeyboardHeight(res)
                        + res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height))
                + getPaddingTop() + getPaddingBottom();
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    /** Total height to occupy — the main keyboard frame's height including the suggestion bar. */
    public void setTotalHeight(final int height) {
        if (height > 0 && height != mTotalHeight) {
            mTotalHeight = height;
            requestLayout();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBackKey = findViewById(R.id.clipboard_back_key);
        mBackKey.setTag(Constants.CODE_ALPHA_FROM_CLIPBOARD);
        mBackKey.setOnTouchListener(this);
        mBackKey.setOnClickListener(this);
        mToggleButton = findViewById(R.id.clipboard_toggle_button);
        mToggleButton.setOnClickListener(this);
        mClearAllButton = findViewById(R.id.clipboard_clear_all_button);
        mClearAllButton.setOnClickListener(this);
        mHistoryList = findViewById(R.id.clipboard_history_list);
        mHistoryList.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mAdapter = new ClipboardHistoryAdapter(mClipboardHistoryManager, this);
        mHistoryList.setAdapter(mAdapter);
        mEmptyView = findViewById(R.id.clipboard_empty_view);
        mEmptyText = findViewById(R.id.clipboard_empty_text);
        mTurnOnButton = findViewById(R.id.clipboard_turn_on_button);
        mTurnOnButton.setBackground(createRoundedBackground(getContext(), 18));
        mTurnOnButton.setOnClickListener(this);
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
    }

    public void setOnClipboardEntryClickedListener(
            final OnClipboardEntryClickedListener listener) {
        mOnClipboardEntryClickedListener = listener;
    }

    public void startClipboardHistory() {
        mClipboardHistoryManager.setOnHistoryChangedListener(this);
        render();
    }

    public void stopClipboardHistory() {
        mClipboardHistoryManager.setOnHistoryChangedListener(null);
    }

    @Override
    public void onClipboardHistoryChanged() {
        post(this::render);
    }

    private void render() {
        final SharedPreferences prefs =
                PreferenceManagerCompat.getDeviceSharedPreferences(getContext());
        final boolean enabled = Settings.readClipboardEnabled(prefs);
        mToggleButton.setContentDescription(getResources().getString(
                enabled ? R.string.clipboard_turn_off : R.string.clipboard_turn_on));
        mToggleButton.setAlpha(enabled ? 1f : 0.5f);
        mClearAllButton.setVisibility(enabled ? VISIBLE : GONE);
        if (!enabled) {
            mHistoryList.setVisibility(GONE);
            mEmptyView.setVisibility(VISIBLE);
            mEmptyText.setText(R.string.clipboard_turned_off);
            mTurnOnButton.setVisibility(VISIBLE);
            return;
        }
        mTurnOnButton.setVisibility(GONE);
        final List<ClipboardHistoryEntry> history = mClipboardHistoryManager.getHistory();
        mAdapter.setEntries(history);
        if (history.isEmpty()) {
            mHistoryList.setVisibility(GONE);
            mEmptyView.setVisibility(VISIBLE);
            mEmptyText.setText(R.string.clipboard_history_empty);
        } else {
            mEmptyView.setVisibility(GONE);
            mHistoryList.setVisibility(VISIBLE);
        }
    }

    private void setClipboardEnabled(final boolean enabled) {
        PreferenceManagerCompat.getDeviceSharedPreferences(getContext()).edit()
                .putBoolean(Settings.PREF_CLIPBOARD_ENABLED, enabled).apply();
        render();
    }

    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        final Object tag = v.getTag();
        if (!(tag instanceof Integer)) {
            return false;
        }
        mKeyboardActionListener.onPressKey(
                (Integer) tag, 0 /* repeatCount */, true /* isSinglePointer */);
        // Returning false keeps {@link #onClick} and touch-down visual feedback working.
        return false;
    }

    @Override
    public void onClick(final View v) {
        if (v == mToggleButton) {
            final SharedPreferences prefs =
                    PreferenceManagerCompat.getDeviceSharedPreferences(getContext());
            setClipboardEnabled(!Settings.readClipboardEnabled(prefs));
            return;
        }
        if (v == mTurnOnButton) {
            setClipboardEnabled(true);
            return;
        }
        if (v == mClearAllButton) {
            mClipboardHistoryManager.clearHistory();
            return;
        }
        final Object tag = v.getTag();
        if (!(tag instanceof Integer)) {
            return;
        }
        final int code = (Integer) tag;
        mKeyboardActionListener.onCodeInput(code, Constants.NOT_A_COORDINATE,
                Constants.NOT_A_COORDINATE, false /* isKeyRepeat */);
        mKeyboardActionListener.onReleaseKey(code, false /* withSliding */);
    }

    @Override
    public void onClipboardEntryClicked(final ClipboardHistoryEntry entry) {
        if (mOnClipboardEntryClickedListener != null) {
            mOnClipboardEntryClickedListener.onClipboardEntryClicked(entry);
        }
    }

    @Override
    public void onClipboardEntryDeleteClicked(final ClipboardHistoryEntry entry) {
        mClipboardHistoryManager.deleteEntry(entry);
    }

    /** Rounded surface tinted from the theme, usable on every keyboard theme incl. night. */
    public static GradientDrawable createRoundedBackground(final Context context,
            final float radiusDp) {
        final GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(resolveCornerRadius(context, radiusDp));
        background.setColor((resolveOnSurfaceVariant(context) & 0x00FFFFFF)
                | 0x24000000 /* ~14% alpha */);
        return background;
    }

    /** M3 assist chip container: subtle fill plus a 1dp outline. */
    public static GradientDrawable createChipBackground(final Context context) {
        final GradientDrawable chip = createRoundedBackground(context, 8);
        final float density = context.getResources().getDisplayMetrics().density;
        chip.setStroke(Math.max(1, (int) density),
                (resolveOnSurfaceVariant(context) & 0x00FFFFFF) | 0x80000000 /* 50% alpha */);
        return chip;
    }

    public static GradientDrawable createRoundedBorder(final Context context,
            final float radiusDp) {
        final float density = context.getResources().getDisplayMetrics().density;
        final GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(resolveCornerRadius(context, radiusDp));
        border.setStroke(Math.max(1, (int) density),
                (resolveOnSurfaceVariant(context) & 0x00FFFFFF) | 0x80000000 /* 50% alpha */);
        return border;
    }

    private static float resolveCornerRadius(final Context context, final float radiusDp) {
        final TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(
                R.attr.clipboardSurfacesRounded, value, true) && value.data == 0) {
            return 0;
        }
        return radiusDp * context.getResources().getDisplayMetrics().density;
    }

    private static int resolveOnSurfaceVariant(final Context context) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.md3OnSurfaceVariant, value, true);
        return (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT)
                ? value.data : context.getResources().getColor(value.resourceId);
    }
}
