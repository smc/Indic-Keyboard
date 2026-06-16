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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.inputmethod.compat.PreferenceManagerCompat;
import com.android.inputmethod.event.Event;
import com.android.inputmethod.keyboard.KeyboardSwitcher;
import com.android.inputmethod.latin.Dictionary;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.SuggestedWords;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.StringUtils;
import com.android.inputmethod.latin.utils.JsonUtils;

import org.smc.inputmethod.indic.LatinIME;
import org.smc.inputmethod.indic.inputlogic.EmojiSearch;
import org.smc.inputmethod.indic.settings.Settings;
import org.smc.inputmethod.indic.suggestions.SuggestionStripView;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the in-keyboard emoji search. The typed query is held in {@link #mQuery} and never sent to
 * the host editor; only a picked emoji is committed. While search is active, key events are routed
 * here by {@link LatinIME} instead of {@link org.smc.inputmethod.indic.inputlogic.InputLogic}.
 */
public final class EmojiSearchController {
    private static final long CARET_BLINK_MILLIS = 500;
    private static final long GROW_ANIM_MILLIS = 220;

    private final LatinIME mLatinIME;
    private final KeyboardSwitcher mKeyboardSwitcher;
    private final SuggestionStripView mStripView;
    private final EmojiSuggestionStripView mEmojiStrip;
    private final View mSearchBar;
    private final View mSearchField;
    private final View mBackButton;
    private final TextView mQueryView;
    private final View mCaret;
    private final View mHint;
    private final EmojiSearch mEmojiSearch;
    private final SharedPreferences mPrefs;
    private final StringBuilder mQuery = new StringBuilder();
    private boolean mActive;
    private ValueAnimator mGrowAnim;

    private final Handler mBlinkHandler = new Handler(Looper.getMainLooper());
    private final Runnable mBlink = new Runnable() {
        @Override
        public void run() {
            mCaret.setVisibility(mCaret.getVisibility() == View.VISIBLE ? View.INVISIBLE
                    : View.VISIBLE);
            mBlinkHandler.postDelayed(this, CARET_BLINK_MILLIS);
        }
    };

    public EmojiSearchController(final LatinIME latinIME, final KeyboardSwitcher keyboardSwitcher,
            final SuggestionStripView stripView, final View searchBar) {
        mLatinIME = latinIME;
        mKeyboardSwitcher = keyboardSwitcher;
        mStripView = stripView;
        mEmojiStrip = (EmojiSuggestionStripView)
                ((View) searchBar.getParent()).findViewById(R.id.emoji_suggestion_strip);
        mSearchBar = searchBar;
        mSearchField = searchBar.findViewById(R.id.emoji_search_field);
        mBackButton = searchBar.findViewById(R.id.emoji_search_back);
        mQueryView = (TextView) searchBar.findViewById(R.id.emoji_search_query);
        mCaret = searchBar.findViewById(R.id.emoji_search_caret);
        mHint = searchBar.findViewById(R.id.emoji_search_hint);
        mEmojiSearch = new EmojiSearch(latinIME);
        mPrefs = PreferenceManagerCompat.getDeviceSharedPreferences(latinIME);
        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                exitSearch();
            }
        });
    }

    public boolean isActive() {
        return mActive;
    }

    public void enterSearch() {
        mActive = true;
        mQuery.setLength(0);
        mSearchBar.setVisibility(View.VISIBLE);
        mStripView.setVisibility(View.GONE);
        mEmojiStrip.setVisibility(View.VISIBLE);
        mKeyboardSwitcher.setEmojiSearchKeyboard();
        startCaretBlink();
        onQueryChanged();
        animateSearchFieldIn();
    }

    public void exitSearch() {
        if (!mActive) {
            return;
        }
        clearState();
        mKeyboardSwitcher.exitEmojiSearchKeyboard();
    }

    /** Leave emoji search and return to the user's previous alphabet layout (language key). */
    public void exitToKeyboard() {
        if (!mActive) {
            return;
        }
        clearState();
        mKeyboardSwitcher.restoreFromEmojiSearch();
        mKeyboardSwitcher.setAlphabetKeyboard();
        mLatinIME.clearSuggestionStrip();
    }

    /**
     * Clear search state without changing the visible keyboard. Used when the input view is torn
     * down (the keyboard is rebuilt from scratch on the next start anyway).
     */
    public void reset() {
        if (!mActive) {
            return;
        }
        clearState();
        mKeyboardSwitcher.restoreFromEmojiSearch();
    }

    private void clearState() {
        mActive = false;
        mQuery.setLength(0);
        stopCaretBlink();
        if (mGrowAnim != null) {
            mGrowAnim.cancel();
            mGrowAnim = null;
        }
        restoreSearchFieldLayout();
        mSearchBar.setVisibility(View.GONE);
        mEmojiStrip.setVisibility(View.GONE);
        mEmojiStrip.clear();
    }

    /**
     * Consume a key event into the search query. Character code points are appended, delete trims
     * the buffer, and everything else (shift, symbol switch, ...) is a no-op here but still lets the
     * caller update the keyboard layout state.
     */
    public void handleEvent(final Event event) {
        if (event.mKeyCode == Constants.CODE_DELETE) {
            final int len = mQuery.length();
            if (len > 0) {
                mQuery.delete(mQuery.offsetByCodePoints(len, -1), len);
            }
            onQueryChanged();
        } else if (event.mCodePoint != Event.NOT_A_CODE_POINT
                && event.mCodePoint >= Constants.CODE_SPACE) {
            mQuery.appendCodePoint(event.mCodePoint);
            onQueryChanged();
        }
    }

    public void handleTextInput(final String text) {
        mQuery.append(text);
        onQueryChanged();
    }

    public void commitPicked(final SuggestedWordInfo info) {
        mLatinIME.commitEmojiFromSearch(info.mWord);
        mQuery.setLength(0);
        onQueryChanged();
    }

    private void startCaretBlink() {
        mCaret.setVisibility(View.VISIBLE);
        mBlinkHandler.removeCallbacks(mBlink);
        mBlinkHandler.postDelayed(mBlink, CARET_BLINK_MILLIS);
    }

    private void stopCaretBlink() {
        mBlinkHandler.removeCallbacks(mBlink);
        mCaret.setVisibility(View.GONE);
    }

    private void onQueryChanged() {
        updateQueryView();
        refreshResults();
    }

    private void updateQueryView() {
        mQueryView.setText(mQuery.toString());
        // Show the hint only while the query is empty (like a focused text field's placeholder).
        mHint.setVisibility(mQuery.length() == 0 ? View.VISIBLE : View.GONE);
    }

    private void refreshResults() {
        final String normalized = mQuery.toString().toLowerCase().replaceAll("[^a-z0-9]", "");
        // Before anything is typed, fill the strip with recents so it isn't blank.
        final ArrayList<SuggestedWordInfo> emojis = normalized.isEmpty()
                ? recentEmojis()
                : mEmojiSearch.search(normalized);
        final SuggestedWords suggestedWords = new SuggestedWords(emojis,
                null /* rawSuggestions */, null /* typedWord */, false /* typedWordValid */,
                false /* willAutoCorrect */, false /* isObsoleteSuggestions */,
                SuggestedWords.INPUT_STYLE_NONE, SuggestedWords.NOT_A_SEQUENCE_NUMBER);
        mEmojiStrip.setSuggestions(suggestedWords, mEmojiClickListener);
    }

    private final EmojiSuggestionStripView.OnEmojiClickListener mEmojiClickListener =
            new EmojiSuggestionStripView.OnEmojiClickListener() {
                @Override
                public void onEmojiClicked(final SuggestedWordInfo info) {
                    commitPicked(info);
                }
            };

    private ArrayList<SuggestedWordInfo> recentEmojis() {
        final ArrayList<SuggestedWordInfo> list = new ArrayList<>();
        final List<Object> keys = JsonUtils.jsonStrToList(Settings.readEmojiRecentKeys(mPrefs));
        for (final Object o : keys) {
            final String emoji;
            if (o instanceof Integer) {
                emoji = StringUtils.newSingleCodePointString((Integer) o);
            } else if (o instanceof String) {
                emoji = (String) o;
            } else {
                continue;
            }
            list.add(new SuggestedWordInfo(emoji, "" /* prevWordsContext */,
                    SuggestedWordInfo.MAX_SCORE - list.size(), SuggestedWordInfo.KIND_COMPLETION,
                    Dictionary.DICTIONARY_RESUMED, SuggestedWordInfo.NOT_AN_INDEX,
                    SuggestedWordInfo.NOT_A_CONFIDENCE));
        }
        return list;
    }

    /**
     * Animate the search box growing from a small pill (its position in the emoji tab bar) to the
     * full-width search field, sliding into place after the back button.
     */
    private void animateSearchFieldIn() {
        mSearchField.setVisibility(View.INVISIBLE);
        mSearchField.post(new Runnable() {
            @Override
            public void run() {
                if (!mActive) {
                    return;
                }
                final int finalWidth = mSearchField.getWidth();
                if (finalWidth <= 0) {
                    mSearchField.setVisibility(View.VISIBLE);
                    return;
                }
                final LinearLayout.LayoutParams lp =
                        (LinearLayout.LayoutParams) mSearchField.getLayoutParams();
                final int startWidth = Math.round(finalWidth * 0.45f);
                final float startTranslation = -mBackButton.getWidth();
                lp.weight = 0;
                lp.width = startWidth;
                mSearchField.setTranslationX(startTranslation);
                mBackButton.setAlpha(0f);
                mSearchField.setVisibility(View.VISIBLE);
                mSearchField.requestLayout();

                final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(GROW_ANIM_MILLIS);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(final ValueAnimator a) {
                        final float t = (Float) a.getAnimatedValue();
                        lp.width = Math.round(startWidth + (finalWidth - startWidth) * t);
                        mSearchField.setTranslationX(startTranslation * (1f - t));
                        mBackButton.setAlpha(t);
                        mSearchField.requestLayout();
                    }
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(final Animator a) {
                        restoreSearchFieldLayout();
                    }
                });
                mGrowAnim = anim;
                anim.start();
            }
        });
    }

    private void restoreSearchFieldLayout() {
        final LinearLayout.LayoutParams lp =
                (LinearLayout.LayoutParams) mSearchField.getLayoutParams();
        lp.width = 0;
        lp.weight = 1f;
        mSearchField.setTranslationX(0f);
        mSearchField.setVisibility(View.VISIBLE);
        mBackButton.setAlpha(1f);
        mSearchField.requestLayout();
    }
}
