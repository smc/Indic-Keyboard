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

package org.smc.inputmethod.indic.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import com.android.inputmethod.keyboard.KeyboardPreviewView;
import com.android.inputmethod.keyboard.KeyboardTheme;
import com.android.inputmethod.latin.R;

/**
 * A layout-enable switch row that expands on tap to show a live preview of the layout in the
 * user's current keyboard theme. Only the switch itself toggles the layout.
 */
public final class LayoutPreviewPreference extends SwitchPreferenceCompat {
    private final InputMethodSubtype mSubtype;
    private boolean mExpanded;

    public LayoutPreviewPreference(final Context context, final InputMethodSubtype subtype) {
        super(context);
        mSubtype = subtype;
        setLayoutResource(R.layout.layout_preview_preference);
        setWidgetLayoutResource(R.layout.preference_material_switch);
    }

    // Row taps expand/collapse the preview instead of toggling the switch.
    @Override
    protected void onClick() {
        mExpanded = !mExpanded;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // The switch handles its own taps (the library listener is attached in
        // super.onBindViewHolder); the widget layout ships non-clickable for rows
        // where the whole row toggles.
        final View switchView = holder.findViewById(androidx.preference.R.id.switchWidget);
        switchView.setClickable(true);
        switchView.setFocusable(true);
        final ImageView indicator =
                (ImageView) holder.findViewById(R.id.layout_expand_indicator);
        indicator.setRotation(mExpanded ? 180f : 0f);
        final FrameLayout previewHolder =
                (FrameLayout) holder.findViewById(R.id.layout_preview_holder);
        previewHolder.setVisibility(mExpanded ? View.VISIBLE : View.GONE);
        previewHolder.setClipToOutline(true);
        if (mExpanded && previewHolder.getTag() != mSubtype) {
            previewHolder.removeAllViews();
            previewHolder.addView(KeyboardPreviewView.create(getContext(),
                    KeyboardTheme.getKeyboardTheme(getContext()), mSubtype),
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            previewHolder.setTag(mSubtype);
        }
    }
}
