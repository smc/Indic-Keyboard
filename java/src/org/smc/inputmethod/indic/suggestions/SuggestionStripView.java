/*
 * Copyright (C) 2011 The Android Open Source Project
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

package org.smc.inputmethod.indic.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsResponse;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.event.Event;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.keyboard.MoreKeysPanel;
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.define.DebugFlags;
import com.android.inputmethod.keyboard.clipboard.ClipboardHistoryView;

import org.smc.inputmethod.indic.InlineAutofillUtils;
import org.smc.inputmethod.indic.clipboard.ClipboardHistoryEntry;
import org.smc.inputmethod.indic.clipboard.ClipboardHistoryManager;
import org.smc.inputmethod.indic.settings.Settings;
import org.smc.inputmethod.indic.settings.SettingsValues;
import org.smc.inputmethod.indic.suggestions.MoreSuggestionsView.MoreSuggestionsListener;
import com.android.inputmethod.latin.utils.ImportantNoticeUtils;
import com.android.inputmethod.latin.utils.ResourceUtils;

import java.util.ArrayList;
import java.util.List;

public final class SuggestionStripView extends RelativeLayout implements OnClickListener,
        OnLongClickListener {
    public interface Listener {
        public void showImportantNoticeContents();
        public void pickSuggestionManually(SuggestedWordInfo word);
        public void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat);
        public void onClipboardChipClicked(ClipboardHistoryEntry entry);
        public void launchSettings();
    }

    static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final float DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.0f;
    private static final float INCOGNITO_GRAY_OUT_ALPHA = 0.5f;

    private final ViewGroup mSuggestionsStrip;
    private final ImageButton mVoiceKey;
    private final ImageButton mMoreSuggestionsKey;
    private final View mImportantNoticeStrip;
    private final View mClipboardChipStrip;
    private final android.widget.HorizontalScrollView mInlineSuggestionsStrip;
    private final android.widget.LinearLayout mInlineSuggestionsContainer;
    private boolean mInlineSuggestionsShowing;
    private boolean mToolboxOpen;
    private final ImageButton mMenuKey;
    private final View mToolboxStrip;
    private final View mToolboxRow;
    private final View mToolboxSettingsKey;
    private final View mToolboxClipboardKey;
    private final View mToolboxEmojiKey;
    private final View mClipboardChipPill;
    private final View mClipboardChipOpenHistory;
    private final android.widget.ImageView mClipboardChipImage;
    private final TextView mClipboardChipText;
    private ClipboardHistoryEntry mClipboardChipEntry;
    MainKeyboardView mMainKeyboardView;

    private final View mMoreSuggestionsContainer;
    private final MoreSuggestionsView mMoreSuggestionsView;
    private final MoreSuggestions.Builder mMoreSuggestionsBuilder;

    private final ArrayList<TextView> mWordViews = new ArrayList<>();
    private final ArrayList<TextView> mDebugInfoViews = new ArrayList<>();
    private final ArrayList<View> mDividerViews = new ArrayList<>();

    Listener mListener;
    private SuggestedWords mSuggestedWords = SuggestedWords.getEmptyInstance();
    private int mStartIndexOfMoreSuggestions;

    private final SuggestionStripLayoutHelper mLayoutHelper;
    private final StripVisibilityGroup mStripVisibilityGroup;

    private static class StripVisibilityGroup {
        private final View mSuggestionStripView;
        private final View mSuggestionsStrip;
        private final View mImportantNoticeStrip;
        private final View mClipboardChipStrip;
        private final View mInlineSuggestionsStrip;
        private final View mToolboxStrip;

        public StripVisibilityGroup(final View suggestionStripView,
                final ViewGroup suggestionsStrip, final View importantNoticeStrip,
                final View clipboardChipStrip, final View inlineSuggestionsStrip,
                final View toolboxStrip) {
            mSuggestionStripView = suggestionStripView;
            mSuggestionsStrip = suggestionsStrip;
            mImportantNoticeStrip = importantNoticeStrip;
            mClipboardChipStrip = clipboardChipStrip;
            mInlineSuggestionsStrip = inlineSuggestionsStrip;
            mToolboxStrip = toolboxStrip;
            showSuggestionsStrip();
        }

        public void setLayoutDirection(final boolean isRtlLanguage) {
            final int layoutDirection = isRtlLanguage ? ViewCompat.LAYOUT_DIRECTION_RTL
                    : ViewCompat.LAYOUT_DIRECTION_LTR;
            ViewCompat.setLayoutDirection(mSuggestionStripView, layoutDirection);
            ViewCompat.setLayoutDirection(mSuggestionsStrip, layoutDirection);
            ViewCompat.setLayoutDirection(mImportantNoticeStrip, layoutDirection);
            ViewCompat.setLayoutDirection(mClipboardChipStrip, layoutDirection);
            ViewCompat.setLayoutDirection(mInlineSuggestionsStrip, layoutDirection);
            ViewCompat.setLayoutDirection(mToolboxStrip, layoutDirection);
        }

        public void showSuggestionsStrip() {
            mSuggestionsStrip.setVisibility(VISIBLE);
            mImportantNoticeStrip.setVisibility(INVISIBLE);
            mClipboardChipStrip.setVisibility(INVISIBLE);
            mInlineSuggestionsStrip.setVisibility(INVISIBLE);
            mToolboxStrip.setVisibility(INVISIBLE);
        }

        public void showImportantNoticeStrip() {
            mSuggestionsStrip.setVisibility(INVISIBLE);
            mImportantNoticeStrip.setVisibility(VISIBLE);
            mClipboardChipStrip.setVisibility(INVISIBLE);
            mInlineSuggestionsStrip.setVisibility(INVISIBLE);
            mToolboxStrip.setVisibility(INVISIBLE);
        }

        public void showClipboardChipStrip() {
            mSuggestionsStrip.setVisibility(INVISIBLE);
            mImportantNoticeStrip.setVisibility(INVISIBLE);
            mClipboardChipStrip.setVisibility(VISIBLE);
            mInlineSuggestionsStrip.setVisibility(INVISIBLE);
            mToolboxStrip.setVisibility(INVISIBLE);
        }

        public void showInlineSuggestionsStrip() {
            mSuggestionsStrip.setVisibility(INVISIBLE);
            mImportantNoticeStrip.setVisibility(INVISIBLE);
            mClipboardChipStrip.setVisibility(INVISIBLE);
            mInlineSuggestionsStrip.setVisibility(VISIBLE);
            mToolboxStrip.setVisibility(INVISIBLE);
        }

        public void showToolboxStrip() {
            mSuggestionsStrip.setVisibility(INVISIBLE);
            mImportantNoticeStrip.setVisibility(INVISIBLE);
            mClipboardChipStrip.setVisibility(INVISIBLE);
            mInlineSuggestionsStrip.setVisibility(INVISIBLE);
            mToolboxStrip.setVisibility(VISIBLE);
        }

        public boolean isShowingImportantNoticeStrip() {
            return mImportantNoticeStrip.getVisibility() == VISIBLE;
        }

        public boolean isShowingClipboardChipStrip() {
            return mClipboardChipStrip.getVisibility() == VISIBLE;
        }

        public boolean isShowingInlineSuggestionsStrip() {
            return mInlineSuggestionsStrip.getVisibility() == VISIBLE;
        }
    }

    /**
     * Construct a {@link SuggestionStripView} for showing suggestions to be picked by the user.
     * @param context
     * @param attrs
     */
    public SuggestionStripView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.suggestionStripViewStyle);
    }

    public SuggestionStripView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);

        final int gutter = ResourceUtils.getKeyboardGutterWidth(context);
        setPadding(gutter, getPaddingTop(), gutter, getPaddingBottom());

        mSuggestionsStrip = (ViewGroup)findViewById(R.id.suggestions_strip);
        mVoiceKey = (ImageButton)findViewById(R.id.suggestions_strip_voice_key);
        mMoreSuggestionsKey = (ImageButton)findViewById(R.id.suggestions_strip_more_key);
        mImportantNoticeStrip = findViewById(R.id.important_notice_strip);
        mClipboardChipStrip = findViewById(R.id.clipboard_chip_strip);
        mClipboardChipPill = findViewById(R.id.clipboard_chip_pill);
        mClipboardChipPill.setBackground(
                ClipboardHistoryView.createChipBackground(context));
        mClipboardChipImage = findViewById(R.id.clipboard_chip_image);
        mClipboardChipImage.setBackground(
                ClipboardHistoryView.createRoundedBackground(context, 6));
        mClipboardChipImage.setClipToOutline(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mClipboardChipImage.setForeground(
                    ClipboardHistoryView.createRoundedBorder(context, 6));
        }
        mClipboardChipText = findViewById(R.id.clipboard_chip_text);
        mClipboardChipStrip.setOnClickListener(this);
        mClipboardChipOpenHistory = findViewById(R.id.clipboard_chip_open_history);
        mClipboardChipOpenHistory.setOnClickListener(this);
        mInlineSuggestionsStrip = findViewById(R.id.inline_suggestions_strip);
        mInlineSuggestionsContainer = findViewById(R.id.inline_suggestions_container);
        mInlineSuggestionsStrip.getViewTreeObserver().addOnScrollChangedListener(
                this::updateInlineSuggestionsFades);
        mToolboxStrip = findViewById(R.id.suggestions_strip_toolbox);
        mToolboxRow = findViewById(R.id.suggestions_strip_toolbox_row);
        mMenuKey = findViewById(R.id.suggestions_strip_menu_key);
        mMenuKey.setOnClickListener(this);
        mToolboxSettingsKey = findViewById(R.id.toolbox_settings_key);
        mToolboxSettingsKey.setOnClickListener(this);
        mToolboxSettingsKey.setBackground(createToolboxButtonBackground(context));
        mToolboxClipboardKey = findViewById(R.id.toolbox_clipboard_key);
        mToolboxClipboardKey.setOnClickListener(this);
        mToolboxClipboardKey.setBackground(createToolboxButtonBackground(context));
        mToolboxEmojiKey = findViewById(R.id.toolbox_emoji_key);
        mToolboxEmojiKey.setOnClickListener(this);
        mToolboxEmojiKey.setBackground(createToolboxButtonBackground(context));
        mStripVisibilityGroup = new StripVisibilityGroup(this, mSuggestionsStrip,
                mImportantNoticeStrip, mClipboardChipStrip, mInlineSuggestionsStrip,
                mToolboxStrip);

        for (int pos = 0; pos < SuggestedWords.MAX_SUGGESTIONS; pos++) {
            final TextView word = new TextView(context, null, R.attr.suggestionWordStyle);
            word.setContentDescription(getResources().getString(R.string.spoken_empty_suggestion));
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            mWordViews.add(word);
            final View divider = inflater.inflate(R.layout.suggestion_divider, null);
            mDividerViews.add(divider);
            final TextView info = new TextView(context, null, R.attr.suggestionWordStyle);
            info.setTextColor(Color.WHITE);
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP);
            mDebugInfoViews.add(info);
        }

        mLayoutHelper = new SuggestionStripLayoutHelper(
                context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = (MoreSuggestionsView)mMoreSuggestionsContainer
                .findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(
                context, mMoreSuggestionsSlidingListener);

        final TypedArray keyboardAttr = context.obtainStyledAttributes(attrs,
                R.styleable.Keyboard, defStyle, R.style.SuggestionStripView);
        final Drawable iconVoice = keyboardAttr.getDrawable(R.styleable.Keyboard_iconShortcutKey);
        final Drawable iconMore = keyboardAttr.getDrawable(R.styleable.Keyboard_iconMoreSuggestionsKey);
        keyboardAttr.recycle();

        mVoiceKey.setImageDrawable(iconVoice);
        mVoiceKey.setOnClickListener(this);

        mMoreSuggestionsKey.setImageDrawable(iconMore);
        mMoreSuggestionsKey.setOnClickListener(this);

        ((android.widget.ImageView) mClipboardChipOpenHistory).setImageDrawable(iconMore);
    }

    /**
     * A connection back to the input method.
     * @param listener
     */
    public void setListener(final Listener listener, final View inputView) {
        mListener = listener;
        mMainKeyboardView = (MainKeyboardView)inputView.findViewById(R.id.keyboard_view);
    }

    public void updateVisibility(final boolean shouldBeVisible, final boolean isFullscreenMode) {
        final int visibility = shouldBeVisible ? VISIBLE : (isFullscreenMode ? GONE : INVISIBLE);
        setVisibility(visibility);
        updateKeys();
    }

    public void setSuggestions(final SuggestedWords suggestedWords, final boolean isRtlLanguage) {
        clear();
        mStripVisibilityGroup.setLayoutDirection(isRtlLanguage);
        mSuggestedWords = suggestedWords;
        mStartIndexOfMoreSuggestions = mLayoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
                getContext(), mSuggestedWords, mSuggestionsStrip, this);
        if (mClipboardChipEntry != null || mInlineSuggestionsShowing) {
            closeToolbox(false /* animate */);
        }
        showSuggestionsOrToolbox();
        if (mClipboardChipEntry != null) {
            // The chip outlives strip clears from panel exits; only an explicit dismissal
            // (typing, paste, keyboard reopen) removes it.
            mStripVisibilityGroup.showClipboardChipStrip();
        } else if (mInlineSuggestionsShowing) {
            mStripVisibilityGroup.showInlineSuggestionsStrip();
        }

        updateMoreSuggestionsKey();
    }

    private void updateMoreSuggestionsKey() {
        if (mSuggestedWords.size() <= mStartIndexOfMoreSuggestions
                || mStripVisibilityGroup.isShowingInlineSuggestionsStrip()
                || mToolboxOpen) {
            mMoreSuggestionsKey.setVisibility(GONE);
        } else {
            mMoreSuggestionsKey.setVisibility(VISIBLE);
        }
    }

    public void setMoreSuggestionsHeight(final int remainingHeight) {
        mLayoutHelper.setMoreSuggestionsHeight(remainingHeight);
    }

    // This method checks if we should show the important notice (checks on permanent storage if
    // it has been shown once already or not, and if in the setup wizard). If applicable, it shows
    // the notice. In all cases, it returns true if it was shown, false otherwise.
    public boolean maybeShowImportantNoticeTitle() {
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        if (!ImportantNoticeUtils.shouldShowImportantNotice(getContext(), currentSettingsValues)) {
            return false;
        }
        if (getWidth() <= 0) {
            return false;
        }
        final String importantNoticeTitle = ImportantNoticeUtils.getSuggestContactsNoticeTitle(
                getContext());
        if (TextUtils.isEmpty(importantNoticeTitle)) {
            return false;
        }
        if (isShowingMoreSuggestionPanel()) {
            dismissMoreSuggestionsPanel();
        }
        closeToolbox(false /* animate */);
        mLayoutHelper.layoutImportantNotice(mImportantNoticeStrip, importantNoticeTitle);
        mStripVisibilityGroup.showImportantNoticeStrip();
        mImportantNoticeStrip.setOnClickListener(this);
        return true;
    }

    public void showClipboardChip(final ClipboardHistoryEntry entry) {
        if (isShowingMoreSuggestionPanel()) {
            dismissMoreSuggestionsPanel();
        }
        closeToolbox(false /* animate */);
        mClipboardChipEntry = entry;
        if (entry.isImage()) {
            mClipboardChipText.setText(getResources().getString(
                    R.string.clipboard_image_label));
            mClipboardChipImage.setImageBitmap(decodeChipThumbnail(entry));
            mClipboardChipImage.setVisibility(VISIBLE);
        } else {
            mClipboardChipText.setText(entry.getDisplayText());
            mClipboardChipImage.setVisibility(GONE);
        }
        mStripVisibilityGroup.showClipboardChipStrip();
    }

    public void dismissClipboardChip() {
        if (mClipboardChipEntry == null) {
            return;
        }
        mClipboardChipEntry = null;
        if (mStripVisibilityGroup.isShowingClipboardChipStrip()) {
            if (mInlineSuggestionsShowing) {
                mStripVisibilityGroup.showInlineSuggestionsStrip();
            } else {
                showSuggestionsOrToolbox();
            }
        }
    }

    private void showSuggestionsOrToolbox() {
        if (mToolboxOpen) {
            mStripVisibilityGroup.showToolboxStrip();
        } else {
            mStripVisibilityGroup.showSuggestionsStrip();
        }
    }

    private static Drawable createToolboxButtonBackground(final Context context) {
        final TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(
                R.attr.suggestionToolboxKeyBackground, value, true) && value.data == 0) {
            final TypedArray a = context.obtainStyledAttributes(null,
                    new int[] { android.R.attr.background }, R.attr.suggestionWordStyle, 0);
            final Drawable background = a.getDrawable(0);
            a.recycle();
            return background;
        }
        return ClipboardHistoryView.createKeyButtonBackground(context);
    }

    private void toggleToolbox() {
        if (mToolboxOpen) {
            closeToolbox(true /* animate */);
        } else {
            openToolbox();
        }
    }

    private void openToolbox() {
        if (isShowingMoreSuggestionPanel()) {
            dismissMoreSuggestionsPanel();
        }
        mToolboxOpen = true;
        morphMenuIcon(true /* open */);
        updateMoreSuggestionsKey();
        mStripVisibilityGroup.showToolboxStrip();
        mToolboxRow.animate().cancel();
        mToolboxRow.setTranslationX(-getWidth() / 4f);
        mToolboxRow.setAlpha(0f);
        mToolboxRow.animate().translationX(0f).alpha(1f).setDuration(150).start();
    }

    private void closeToolbox(final boolean animate) {
        if (!mToolboxOpen) {
            return;
        }
        mToolboxOpen = false;
        morphMenuIcon(false /* open */);
        mToolboxRow.animate().cancel();
        if (animate && mToolboxStrip.getVisibility() == VISIBLE) {
            mToolboxRow.animate().translationX(-getWidth() / 4f).alpha(0f).setDuration(150)
                    .withEndAction(this::finishToolboxClose).start();
        } else {
            finishToolboxClose();
        }
    }

    private void finishToolboxClose() {
        mToolboxRow.setTranslationX(0f);
        mToolboxRow.setAlpha(1f);
        updateMoreSuggestionsKey();
        if (mToolboxStrip.getVisibility() != VISIBLE) {
            return;
        }
        if (mClipboardChipEntry != null) {
            mStripVisibilityGroup.showClipboardChipStrip();
        } else if (mInlineSuggestionsShowing) {
            mStripVisibilityGroup.showInlineSuggestionsStrip();
        } else {
            mStripVisibilityGroup.showSuggestionsStrip();
        }
    }

    private void morphMenuIcon(final boolean open) {
        mMenuKey.animate().cancel();
        mMenuKey.animate().alpha(0f).rotation(open ? 90f : -90f).setDuration(90)
                .withEndAction(() -> {
                    mMenuKey.setImageResource(open
                            ? R.drawable.ic_kb_menu_open : R.drawable.ic_kb_menu);
                    mMenuKey.setRotation(open ? -90f : 90f);
                    mMenuKey.animate().alpha(1f).rotation(0f).setDuration(90).start();
                }).start();
    }

    private int mInlineSuggestionsGeneration;
    private boolean mInlineSuggestionsBlocked;

    public void onStartInputView() {
        invalidateInlineSuggestions(false /* blockUntilRefocus */);
        closeToolbox(false /* animate */);
    }

    public void onWindowHidden() {
        invalidateInlineSuggestions(false /* blockUntilRefocus */);
        closeToolbox(false /* animate */);
    }

    public void onCodeInputEvent(final Event event) {
        if (event.isFunctionalKeyEvent()) {
            switch (event.mKeyCode) {
                case Constants.CODE_DELETE:
                case Constants.CODE_SHIFT_ENTER:
                case Constants.CODE_ACTION_NEXT:
                case Constants.CODE_ACTION_PREVIOUS:
                    break;
                default:
                    return;
            }
        }
        dismissTransientStrips();
    }

    public void dismissTransientStrips() {
        invalidateInlineSuggestions(true /* blockUntilRefocus */);
        dismissClipboardChip();
        closeToolbox(true /* animate */);
    }

    private void invalidateInlineSuggestions(final boolean blockUntilRefocus) {
        mInlineSuggestionsGeneration++;
        mInlineSuggestionsBlocked = blockUntilRefocus;
        dismissInlineSuggestions();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    public boolean onInlineSuggestionsResponse(final InlineSuggestionsResponse response) {
        if (mInlineSuggestionsBlocked) {
            return true;
        }
        final int generation = ++mInlineSuggestionsGeneration;
        final List<InlineSuggestion> suggestions = response.getInlineSuggestions();
        boolean hasCredentials = false;
        for (final InlineSuggestion suggestion : suggestions) {
            if (!suggestion.getInfo().isPinned()) {
                hasCredentials = true;
                break;
            }
        }
        if (!hasCredentials) {
            dismissInlineSuggestions();
            return true;
        }
        InlineAutofillUtils.inflate(suggestions, getContext(), (views, pinnedView) -> {
            if (generation != mInlineSuggestionsGeneration) {
                return;
            }
            if (views.isEmpty()) {
                dismissInlineSuggestions();
            } else {
                showInlineSuggestions(views, pinnedView);
            }
        });
        return true;
    }

    public boolean isShowingClipboardChip() {
        return mStripVisibilityGroup.isShowingClipboardChipStrip();
    }

    private void showInlineSuggestions(final ArrayList<View> suggestionViews,
            final View pinnedView) {
        if (isShowingMoreSuggestionPanel()) {
            dismissMoreSuggestionsPanel();
        }
        closeToolbox(false /* animate */);
        mInlineSuggestionsContainer.removeAllViews();
        mInlineSuggestionsContainer.setGravity(suggestionViews.size() <= 1
                ? android.view.Gravity.CENTER
                : android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        final int margin = (int) (getResources().getDisplayMetrics().density * 4);
        for (final View view : suggestionViews) {
            addInlineChip(view, margin);
        }
        if (pinnedView != null) {
            addInlineChip(pinnedView, margin);
        }
        mInlineSuggestionsShowing = true;
        mInlineSuggestionsStrip.scrollTo(0, 0);
        mInlineSuggestionsContainer.post(this::updateInlineSuggestionsFades);
        // A live clipboard chip keeps priority; the inline strip takes over once it is dismissed.
        if (mClipboardChipEntry == null) {
            mStripVisibilityGroup.showInlineSuggestionsStrip();
            mMoreSuggestionsKey.setVisibility(GONE);
            mVoiceKey.setVisibility(GONE);
        }
    }

    private void updateInlineSuggestionsFades() {
        if (!mInlineSuggestionsShowing) {
            return;
        }
        final int viewportLeft = mInlineSuggestionsStrip.getScrollX();
        final int viewportRight = viewportLeft + mInlineSuggestionsStrip.getWidth();
        for (int i = 0; i < mInlineSuggestionsContainer.getChildCount(); i++) {
            final View chip = mInlineSuggestionsContainer.getChildAt(i);
            final int width = chip.getWidth();
            if (width == 0) {
                continue;
            }
            final int visible = Math.min(chip.getRight(), viewportRight)
                    - Math.max(chip.getLeft(), viewportLeft);
            setChipAlpha(chip, Math.max(0f, Math.min(1f, visible / (float) width)));
        }
    }

    private static void setChipAlpha(final View view, final float alpha) {
        view.setAlpha(alpha);
        if (view instanceof android.view.SurfaceView) {
            return;
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setChipAlpha(group.getChildAt(i), alpha);
            }
        }
    }

    private void addInlineChip(final View view, final int margin) {
        final android.view.ViewGroup.LayoutParams delivered = view.getLayoutParams();
        final android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(
                        delivered != null ? delivered.width
                                : android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        delivered != null ? delivered.height
                                : android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.CENTER_VERTICAL;
        params.leftMargin = margin;
        params.rightMargin = margin;
        mInlineSuggestionsContainer.addView(view, params);
    }

    private void dismissInlineSuggestions() {
        if (!mInlineSuggestionsShowing) {
            return;
        }
        mInlineSuggestionsShowing = false;
        mInlineSuggestionsContainer.removeAllViews();
        if (mStripVisibilityGroup.isShowingInlineSuggestionsStrip()) {
            showSuggestionsOrToolbox();
            updateKeys();
        }
    }

    private android.graphics.Bitmap decodeChipThumbnail(final ClipboardHistoryEntry entry) {
        final String path = ClipboardHistoryManager.init(getContext())
                .imageFileFor(entry).getAbsolutePath();
        final android.graphics.BitmapFactory.Options options =
                new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(path, options);
        int sampleSize = 1;
        while (options.outWidth / (sampleSize * 2) >= 128
                && options.outHeight / (sampleSize * 2) >= 128) {
            sampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        return android.graphics.BitmapFactory.decodeFile(path, options);
    }

    public void clear() {
        mSuggestionsStrip.removeAllViews();
        removeAllDebugInfoViews();
        mStripVisibilityGroup.showSuggestionsStrip();
        dismissMoreSuggestionsPanel();
        mMoreSuggestionsKey.setVisibility(INVISIBLE);
    }

    private void removeAllDebugInfoViews() {
        // The debug info views may be placed as children views of this {@link SuggestionStripView}.
        for (final View debugInfoView : mDebugInfoViews) {
            final ViewParent parent = debugInfoView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup)parent).removeView(debugInfoView);
            }
        }
    }

    private final MoreSuggestionsListener mMoreSuggestionsListener = new MoreSuggestionsListener() {
        @Override
        public void onSuggestionSelected(final SuggestedWordInfo wordInfo) {
            mListener.pickSuggestionManually(wordInfo);
            dismissMoreSuggestionsPanel();
        }

        @Override
        public void onCancelInput() {
            dismissMoreSuggestionsPanel();
        }
    };

    private final MoreKeysPanel.Controller mMoreSuggestionsController =
            new MoreKeysPanel.Controller() {
        @Override
        public void onDismissMoreKeysPanel() {
            mMainKeyboardView.onDismissMoreKeysPanel();
        }

        @Override
        public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
            mMainKeyboardView.onShowMoreKeysPanel(panel);
        }

        @Override
        public void onCancelMoreKeysPanel() {
            dismissMoreSuggestionsPanel();
        }
    };

    public boolean isShowingMoreSuggestionPanel() {
        return mMoreSuggestionsView.isShowingInParent();
    }

    public void dismissMoreSuggestionsPanel() {
        mMoreSuggestionsView.dismissMoreKeysPanel();
    }

    @Override
    public boolean onLongClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.NOT_A_CODE, this);
        return showMoreSuggestions();
    }

    boolean showMoreSuggestions() {
        final Keyboard parentKeyboard = mMainKeyboardView.getKeyboard();
        if (parentKeyboard == null) {
            return false;
        }
        final SuggestionStripLayoutHelper layoutHelper = mLayoutHelper;
        if (mSuggestedWords.size() <= mStartIndexOfMoreSuggestions) {
            return false;
        }
        final int stripWidth = getWidth();
        final View container = mMoreSuggestionsContainer;
        final int maxWidth = stripWidth - container.getPaddingLeft() - container.getPaddingRight();
        final MoreSuggestions.Builder builder = mMoreSuggestionsBuilder;
        builder.layout(mSuggestedWords, mStartIndexOfMoreSuggestions, maxWidth,
                (int)(maxWidth * layoutHelper.mMinMoreSuggestionsWidth),
                layoutHelper.getMaxMoreSuggestionsRow(), parentKeyboard);
        mMoreSuggestionsView.setKeyboard(builder.build());
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final MoreKeysPanel moreKeysPanel = mMoreSuggestionsView;
        final int pointX = stripWidth / 2;
        final int pointY = -layoutHelper.mMoreSuggestionsBottomGap;
        moreKeysPanel.showMoreKeysPanel(this, mMoreSuggestionsController, pointX, pointY,
                mMoreSuggestionsListener);
        mOriginX = mLastX;
        mOriginY = mLastY;
        for (int i = 0; i < mStartIndexOfMoreSuggestions; i++) {
            mWordViews.get(i).setPressed(false);
        }
        return true;
    }

    // Working variables for {@link onInterceptTouchEvent(MotionEvent)} and
    // {@link onTouchEvent(MotionEvent)}.
    private int mLastX;
    private int mLastY;
    private int mOriginX;
    private int mOriginY;
    private final int mMoreSuggestionsModalTolerance;
    private boolean mNeedsToTransformTouchEventToHoverEvent;
    private boolean mIsDispatchingHoverEventToMoreSuggestions;
    private final GestureDetector mMoreSuggestionsSlidingDetector;
    private final GestureDetector.OnGestureListener mMoreSuggestionsSlidingListener =
            new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent down, MotionEvent me, float deltaX, float deltaY) {
            if (down == null) {
                return false;
            }
            final float dy = me.getY() - down.getY();
            if (deltaY > 0 && dy < 0) {
                return showMoreSuggestions();
            }
            return false;
        }
    };

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent me) {
        if (mStripVisibilityGroup.isShowingImportantNoticeStrip()
                || mStripVisibilityGroup.isShowingClipboardChipStrip()) {
            return false;
        }
        // Detecting sliding up finger to show {@link MoreSuggestionsView}.
        if (!mMoreSuggestionsView.isShowingInParent()) {
            mLastX = (int)me.getX();
            mLastY = (int)me.getY();
            return mMoreSuggestionsSlidingDetector.onTouchEvent(me);
        }
        if (mMoreSuggestionsView.isInModalMode()) {
            return false;
        }

        final int action = me.getAction();
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);
        if (Math.abs(x - mOriginX) >= mMoreSuggestionsModalTolerance
                || mOriginY - y >= mMoreSuggestionsModalTolerance) {
            // Decided to be in the sliding suggestion mode only when the touch point has been moved
            // upward. Further {@link MotionEvent}s will be delivered to
            // {@link #onTouchEvent(MotionEvent)}.
            mNeedsToTransformTouchEventToHoverEvent =
                    AccessibilityUtils.getInstance().isTouchExplorationEnabled();
            mIsDispatchingHoverEventToMoreSuggestions = false;
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            // Decided to be in the modal input mode.
            mMoreSuggestionsView.setModalMode();
        }
        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
        // Don't populate accessibility event with suggested words and voice key.
        return true;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        if (!mMoreSuggestionsView.isShowingInParent()) {
            // Ignore any touch event while more suggestions panel hasn't been shown.
            // Detecting sliding up is done at {@link #onInterceptTouchEvent}.
            return true;
        }
        // In the sliding input mode. {@link MotionEvent} should be forwarded to
        // {@link MoreSuggestionsView}.
        final int index = me.getActionIndex();
        final int x = mMoreSuggestionsView.translateX((int)me.getX(index));
        final int y = mMoreSuggestionsView.translateY((int)me.getY(index));
        me.setLocation(x, y);
        if (!mNeedsToTransformTouchEventToHoverEvent) {
            mMoreSuggestionsView.onTouchEvent(me);
            return true;
        }
        // In sliding suggestion mode with accessibility mode on, a touch event should be
        // transformed to a hover event.
        final int width = mMoreSuggestionsView.getWidth();
        final int height = mMoreSuggestionsView.getHeight();
        final boolean onMoreSuggestions = (x >= 0 && x < width && y >= 0 && y < height);
        if (!onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Just drop this touch event because dispatching hover event isn't started yet and
            // the touch event isn't on {@link MoreSuggestionsView}.
            return true;
        }
        final int hoverAction;
        if (onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Transform this touch event to a hover enter event and start dispatching a hover
            // event to {@link MoreSuggestionsView}.
            mIsDispatchingHoverEventToMoreSuggestions = true;
            hoverAction = MotionEvent.ACTION_HOVER_ENTER;
        } else if (me.getActionMasked() == MotionEvent.ACTION_UP) {
            // Transform this touch event to a hover exit event and stop dispatching a hover event
            // after this.
            mIsDispatchingHoverEventToMoreSuggestions = false;
            mNeedsToTransformTouchEventToHoverEvent = false;
            hoverAction = MotionEvent.ACTION_HOVER_EXIT;
        } else {
            // Transform this touch event to a hover move event.
            hoverAction = MotionEvent.ACTION_HOVER_MOVE;
        }
        me.setAction(hoverAction);
        mMoreSuggestionsView.onHoverEvent(me);
        return true;
    }

    @Override
    public void onClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.CODE_UNSPECIFIED, this);
        if (view == mImportantNoticeStrip) {
            mListener.showImportantNoticeContents();
            return;
        }
        if (view == mClipboardChipOpenHistory) {
            mListener.onCodeInput(Constants.CODE_CLIPBOARD,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false /* isKeyRepeat */);
            return;
        }
        if (view == mClipboardChipStrip) {
            final ClipboardHistoryEntry entry = mClipboardChipEntry;
            if (entry != null) {
                mListener.onClipboardChipClicked(entry);
            }
            return;
        }
        if (view == mVoiceKey) {
            mListener.onCodeInput(Constants.CODE_SHORTCUT,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false /* isKeyRepeat */);
            return;
        }
        if (view == mMenuKey) {
            toggleToolbox();
            return;
        }
        if (view == mToolboxSettingsKey) {
            mListener.launchSettings();
            return;
        }
        if (view == mToolboxClipboardKey) {
            mListener.onCodeInput(Constants.CODE_CLIPBOARD,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false /* isKeyRepeat */);
            return;
        }
        if (view == mToolboxEmojiKey) {
            mListener.onCodeInput(Constants.CODE_EMOJI,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false /* isKeyRepeat */);
            return;
        }
        if (view == mMoreSuggestionsKey) {
            if (isShowingMoreSuggestionPanel()) {
                mMoreSuggestionsView.dismissMoreKeysPanel();
            } else if (showMoreSuggestions()) {
                mMoreSuggestionsView.setModalMode();
            }
            return;
        }

        final Object tag = view.getTag();
        // {@link Integer} tag is set at
        // {@link SuggestionStripLayoutHelper#setupWordViewsTextAndColor(SuggestedWords,int)} and
        // {@link SuggestionStripLayoutHelper#layoutPunctuationSuggestions(SuggestedWords,ViewGroup}
        if (tag instanceof Integer) {
            final int index = (Integer) tag;
            if (index >= mSuggestedWords.size()) {
                return;
            }
            final SuggestedWordInfo wordInfo = mSuggestedWords.getInfo(index);
            mListener.pickSuggestionManually(wordInfo);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissMoreSuggestionsPanel();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overriden by showing suggestions later, if applicable.
        if (oldw <= 0 && w > 0) {
            maybeShowImportantNoticeTitle();
        }
    }

    private void updateKeys() {
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        final boolean grayOut = currentSettingsValues.mIncognitoModeEnabled
                && currentSettingsValues.mGrayOutSuggestionsInIncognito;
        mVoiceKey.setVisibility(currentSettingsValues.mShowsVoiceInputKey ? VISIBLE : GONE);
        mSuggestionsStrip.setAlpha(grayOut ? INCOGNITO_GRAY_OUT_ALPHA : 1.0f);
        positionRightKeys();
    }

    private void positionRightKeys() {
        final boolean voiceShown = mVoiceKey.getVisibility() == VISIBLE;
        final int edge = getResources().getDimensionPixelSize(
                R.dimen.config_suggestions_strip_edge_key_width);
        final int baseMargin = getResources().getDimensionPixelSize(
                R.dimen.config_suggestions_strip_horizontal_margin);
        final int moreMargin = voiceShown ? edge : 0;
        final int stripMargin = baseMargin + (voiceShown ? edge : 0);
        final RelativeLayout.LayoutParams moreLp =
                (RelativeLayout.LayoutParams) mMoreSuggestionsKey.getLayoutParams();
        if (moreLp.rightMargin != moreMargin) {
            moreLp.rightMargin = moreMargin;
            mMoreSuggestionsKey.setLayoutParams(moreLp);
        }
        final RelativeLayout.LayoutParams stripLp =
                (RelativeLayout.LayoutParams) mSuggestionsStrip.getLayoutParams();
        if (stripLp.rightMargin != stripMargin) {
            stripLp.rightMargin = stripMargin;
            mSuggestionsStrip.setLayoutParams(stripLp);
        }
    }
}
