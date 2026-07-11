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
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import com.android.inputmethod.keyboard.KeyboardPreviewView;
import com.android.inputmethod.keyboard.KeyboardTheme;
import com.android.inputmethod.latin.R;

/**
 * A layout-enable switch rendered as its own card: a live preview of the layout in the user's
 * current keyboard theme, with the layout name and switch on a footer row.
 */
public final class LayoutPreviewPreference extends SwitchPreferenceCompat
        implements CardedPreferenceGroupAdapter.Standalone {
    private final InputMethodSubtype mSubtype;

    public LayoutPreviewPreference(final Context context, final InputMethodSubtype subtype) {
        super(context);
        mSubtype = subtype;
        setLayoutResource(R.layout.layout_preview_preference);
        setWidgetLayoutResource(R.layout.preference_material_switch);
    }

    @Override
    public void onBindViewHolder(final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // The card background (set by CardedPreferenceGroupAdapter) has rounded corners; clip
        // the edge-to-edge preview to them.
        holder.itemView.setClipToOutline(true);
        final FrameLayout previewHolder =
                (FrameLayout) holder.findViewById(R.id.layout_preview_holder);
        if (previewHolder.getTag() != mSubtype) {
            previewHolder.removeAllViews();
            previewHolder.addView(KeyboardPreviewView.create(getContext(),
                    KeyboardTheme.getKeyboardTheme(getContext()), mSubtype),
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            previewHolder.setTag(mSubtype);
        }
    }
}
