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

import java.util.ArrayList;
import java.util.List;

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
        final GridLayoutManager layoutManager = new GridLayoutManager(context, spanCount);
        final ThemeAdapter adapter = new ThemeAdapter(context);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(final int position) {
                return adapter.isHeader(position) ? spanCount : 1;
            }
        });
        grid.setLayoutManager(layoutManager);
        grid.setAdapter(adapter);
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

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_THEME = 1;

    private static final class Item {
        final boolean header;
        final int themeId;
        final String label;
        final String description;

        Item(final boolean header, final int themeId, final String label,
                final String description) {
            this.header = header;
            this.themeId = themeId;
            this.label = label;
            this.description = description;
        }
    }

    private final class ThemeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Item> mItems = new ArrayList<>();
        private final InputMethodSubtype mSubtype;

        ThemeAdapter(final Context context) {
            final Resources res = context.getResources();
            final String[] names = res.getStringArray(R.array.keyboard_theme_short_names);
            final int[] themeIds = res.getIntArray(R.array.keyboard_theme_ids);
            final String[] groups = res.getStringArray(R.array.keyboard_theme_groups);
            final String[] groupDescs = res.getStringArray(R.array.keyboard_theme_group_descs);
            String currentGroup = null;
            for (int index = 0; index < themeIds.length; index++) {
                if (!groups[index].equals(currentGroup)) {
                    currentGroup = groups[index];
                    mItems.add(new Item(true, -1, currentGroup, groupDescs[index]));
                }
                mItems.add(new Item(false, themeIds[index], names[index], null));
            }
            mSubtype = RichInputMethodManager.getInstance().getCurrentSubtype().getRawSubtype();
        }

        boolean isHeader(final int position) {
            return mItems.get(position).header;
        }

        @Override
        public int getItemViewType(final int position) {
            return mItems.get(position).header ? VIEW_TYPE_HEADER : VIEW_TYPE_THEME;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent,
                final int viewType) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_HEADER) {
                return new HeaderViewHolder(
                        inflater.inflate(R.layout.keyboard_theme_group_header, parent, false));
            }
            return new ThemeViewHolder(
                    inflater.inflate(R.layout.keyboard_theme_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
            final Item item = mItems.get(position);
            if (item.header) {
                ((HeaderViewHolder) holder).bind(item.label, item.description);
                return;
            }
            final ThemeViewHolder themeHolder = (ThemeViewHolder) holder;
            themeHolder.bind(item.themeId, item.label, mSubtype, item.themeId == mSelectedThemeId);
            themeHolder.itemView.setOnClickListener(v -> selectTheme(item.themeId));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        private void selectTheme(final int themeId) {
            if (themeId == mSelectedThemeId) {
                return;
            }
            mSelectedThemeId = themeId;
            KeyboardTheme.saveKeyboardThemeId(themeId,
                    PreferenceManagerCompat.getDeviceSharedPreferences(requireContext()));
            for (int index = 0; index < mItems.size(); index++) {
                if (!mItems.get(index).header) {
                    notifyItemChanged(index);
                }
            }
        }
    }

    private static final class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTitle;
        private final TextView mDescription;

        HeaderViewHolder(final View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.theme_group_title);
            mDescription = itemView.findViewById(R.id.theme_group_desc);
        }

        void bind(final String label, final String description) {
            mTitle.setText(label);
            mDescription.setText(description);
        }
    }

    private final class ThemeViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView mCard;
        private final FrameLayout mPreviewHolder;
        private final TextView mName;
        private final View mSelectedIcon;
        private final int mCheckedStrokeColor;
        private final int mUncheckedStrokeColor;
        private int mBoundThemeId = -1;

        ThemeViewHolder(final View itemView) {
            super(itemView);
            mCard = itemView.findViewById(R.id.theme_card);
            mPreviewHolder = itemView.findViewById(R.id.theme_preview_holder);
            mName = itemView.findViewById(R.id.theme_name);
            mSelectedIcon = itemView.findViewById(R.id.theme_selected_icon);
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
            mSelectedIcon.setVisibility(selected ? View.VISIBLE : View.GONE);
            mCard.setChecked(selected);
            mCard.setStrokeWidth(dp(selected ? 2 : 1));
            mCard.setStrokeColor(selected ? mCheckedStrokeColor : mUncheckedStrokeColor);
            mCard.setContentDescription(name);
        }
    }
}
