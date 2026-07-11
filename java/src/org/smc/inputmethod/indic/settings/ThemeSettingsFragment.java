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

package org.smc.inputmethod.indic.settings;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.inputmethod.compat.PreferenceManagerCompat;
import com.android.inputmethod.keyboard.KeyboardPreviewView;
import com.android.inputmethod.keyboard.KeyboardTheme;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/**
 * "Keyboard theme" settings sub screen: a grid of live keyboard previews, one per theme,
 * rendered with the user's current layout. Tapping a card applies the theme immediately.
 */
public final class ThemeSettingsFragment extends Fragment {
    private int mSelectedThemeId;

    static void updateKeyboardThemeSummary(final Preference pref) {
        final Context context = pref.getContext();
        final Resources res = context.getResources();
        final KeyboardTheme keyboardTheme = KeyboardTheme.getKeyboardTheme(context);
        final String[] keyboardThemeNames = res.getStringArray(R.array.keyboard_theme_names);
        final int[] keyboardThemeIds = res.getIntArray(R.array.keyboard_theme_ids);
        for (int index = 0; index < keyboardThemeNames.length; index++) {
            if (keyboardTheme.mThemeId == keyboardThemeIds[index]) {
                pref.setSummary(keyboardThemeNames[index]);
                return;
            }
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final Context context = requireContext();
        RichInputMethodManager.init(context);
        mSelectedThemeId = KeyboardTheme.getKeyboardTheme(context).mThemeId;

        final RecyclerView grid = new RecyclerView(context);
        final int padding = dp(6);
        grid.setPadding(padding, padding, padding, padding);
        grid.setClipToPadding(false);
        final int spanCount = Math.max(2,
                getResources().getConfiguration().screenWidthDp / 280);
        grid.setLayoutManager(new GridLayoutManager(context, spanCount));
        grid.setAdapter(new ThemeAdapter(context));
        return grid;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.settings_screen_theme);
            }
        }
    }

    private int dp(final int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private final class ThemeAdapter extends RecyclerView.Adapter<ThemeViewHolder> {
        private final String[] mNames;
        private final int[] mThemeIds;
        private final InputMethodSubtype mSubtype;

        ThemeAdapter(final Context context) {
            final Resources res = context.getResources();
            mNames = res.getStringArray(R.array.keyboard_theme_names);
            mThemeIds = res.getIntArray(R.array.keyboard_theme_ids);
            mSubtype = RichInputMethodManager.getInstance().getCurrentSubtype().getRawSubtype();
        }

        @Override
        public ThemeViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            final View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.keyboard_theme_item, parent, false);
            return new ThemeViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ThemeViewHolder holder, final int position) {
            final int themeId = mThemeIds[position];
            holder.bind(themeId, mNames[position], mSubtype, themeId == mSelectedThemeId);
            holder.itemView.setOnClickListener(v -> selectTheme(themeId));
        }

        @Override
        public int getItemCount() {
            return mThemeIds.length;
        }

        private void selectTheme(final int themeId) {
            if (themeId == mSelectedThemeId) {
                return;
            }
            mSelectedThemeId = themeId;
            KeyboardTheme.saveKeyboardThemeId(themeId,
                    PreferenceManagerCompat.getDeviceSharedPreferences(requireContext()));
            for (int index = 0; index < mThemeIds.length; index++) {
                notifyItemChanged(index);
            }
        }
    }

    private final class ThemeViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView mCard;
        private final FrameLayout mPreviewHolder;
        private final TextView mName;
        private final int mCheckedStrokeColor;
        private final int mUncheckedStrokeColor;
        private int mBoundThemeId = -1;

        ThemeViewHolder(final View itemView) {
            super(itemView);
            mCard = itemView.findViewById(R.id.theme_card);
            mPreviewHolder = itemView.findViewById(R.id.theme_preview_holder);
            mName = itemView.findViewById(R.id.theme_name);
            mCard.setClipToOutline(true);
            mCheckedStrokeColor = MaterialColors.getColor(mCard,
                    androidx.appcompat.R.attr.colorPrimary);
            mUncheckedStrokeColor = MaterialColors.getColor(mCard,
                    com.google.android.material.R.attr.colorOutlineVariant);
        }

        void bind(final int themeId, final String name, final InputMethodSubtype subtype,
                final boolean selected) {
            if (themeId != mBoundThemeId) {
                mPreviewHolder.removeAllViews();
                mPreviewHolder.addView(KeyboardPreviewView.create(itemView.getContext(),
                        KeyboardTheme.getKeyboardTheme(themeId), subtype),
                        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                mBoundThemeId = themeId;
            }
            mName.setText(name);
            mCard.setChecked(selected);
            mCard.setStrokeWidth(dp(selected ? 2 : 1));
            mCard.setStrokeColor(selected ? mCheckedStrokeColor : mUncheckedStrokeColor);
            mCard.setContentDescription(name);
        }
    }
}
