/*
 * Copyright (C) 2014 The Android Open Source Project
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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.latin.R;

final class EmojiPalettesAdapter extends RecyclerView.Adapter<EmojiPalettesAdapter.ViewHolder> {
    private static final String TAG = EmojiPalettesAdapter.class.getSimpleName();
    private static final boolean DEBUG_PAGER = false;

    private final EmojiPageKeyboardView.OnKeyEventListener mListener;
    private final DynamicGridKeyboard mRecentsKeyboard;
    private final SparseArray<EmojiPageKeyboardView> mActiveKeyboardViews = new SparseArray<>();
    private final EmojiCategory mEmojiCategory;
    private int mActivePosition = 0;

    public EmojiPalettesAdapter(final EmojiCategory emojiCategory,
            final EmojiPageKeyboardView.OnKeyEventListener listener) {
        mEmojiCategory = emojiCategory;
        mListener = listener;
        mRecentsKeyboard = mEmojiCategory.getKeyboard(EmojiCategory.ID_RECENTS, 0);
    }

    public void flushPendingRecentKeys() {
        mRecentsKeyboard.flushPendingRecentKeys();
        final KeyboardView recentKeyboardView =
                mActiveKeyboardViews.get(mEmojiCategory.getRecentTabId());
        if (recentKeyboardView != null) {
            recentKeyboardView.invalidateAllKeys();
        }
    }

    public void addRecentKey(final Key key) {
        if (mEmojiCategory.isInRecentTab()) {
            mRecentsKeyboard.addPendingKey(key);
            return;
        }
        mRecentsKeyboard.addKeyFirst(key);
        final KeyboardView recentKeyboardView =
                mActiveKeyboardViews.get(mEmojiCategory.getRecentTabId());
        if (recentKeyboardView != null) {
            recentKeyboardView.invalidateAllKeys();
        }
    }

    public void removeRecentKey(final Key key) {
        mRecentsKeyboard.removeKey(key);
    }

    /** Redraw the recents page after its keys were reloaded externally (e.g. an emoji search pick). */
    public void invalidateRecents() {
        final EmojiPageKeyboardView recentKeyboardView =
                mActiveKeyboardViews.get(mEmojiCategory.getRecentTabId());
        if (recentKeyboardView != null) {
            recentKeyboardView.setKeyboard(mRecentsKeyboard);
            recentKeyboardView.invalidateAllKeys();
        }
    }

    public void onPageScrolled() {
        releaseCurrentKey(false /* withKeyRegistering */);
    }

    /** Scrolls the (already-visible) page at {@code position} back to the top. */
    public void scrollPageToTop(final int position) {
        final EmojiPageKeyboardView view = mActiveKeyboardViews.get(position);
        if (view != null && view.getParent() instanceof ScrollView) {
            ((ScrollView) view.getParent()).smoothScrollTo(0, 0);
        }
    }

    public void releaseCurrentKey(final boolean withKeyRegistering) {
        // Make sure the delayed key-down event (highlight effect and haptic feedback) will be
        // canceled.
        final EmojiPageKeyboardView currentKeyboardView =
                mActiveKeyboardViews.get(mActivePosition);
        if (currentKeyboardView == null) {
            return;
        }
        currentKeyboardView.releaseCurrentKey(withKeyRegistering);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View page = inflater.inflate(R.layout.emoji_keyboard_page, parent, false);
        return new ViewHolder(page);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiPalettesAdapter.ViewHolder holder, int position) {
        if (DEBUG_PAGER) {
            Log.d(TAG, "instantiate item: " + position);
        }
        final EmojiPageKeyboardView oldKeyboardView = mActiveKeyboardViews.get(position);
        if (oldKeyboardView != null) {
            oldKeyboardView.deallocateMemory();
            // This may be redundant but wanted to be safer..
            mActiveKeyboardViews.remove(position);
        }
        final Keyboard keyboard =
                mEmojiCategory.getKeyboardFromPagePosition(position);
        holder.getKeyboardView().setKeyboard(keyboard);
        holder.getKeyboardView().setOnKeyEventListener(mListener);
        holder.itemView.scrollTo(0, 0);
        mActiveKeyboardViews.put(position, holder.getKeyboardView());
    }

    @Override
    public int getItemCount() {
        return mEmojiCategory.getTotalPageCountOfAllCategories();
    }


    static class ViewHolder extends RecyclerView.ViewHolder {
        private final EmojiPageKeyboardView customView;

        public ViewHolder(View v) {
            super(v);
            customView = v.findViewById(R.id.emoji_keyboard_page);
        }

        public EmojiPageKeyboardView getKeyboardView() {
            return customView;
        }

    }
}
