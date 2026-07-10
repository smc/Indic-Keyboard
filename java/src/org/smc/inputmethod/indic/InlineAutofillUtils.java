/*
 * Copyright 2026, Jishnu Mohan <jishnu7@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.smc.inputmethod.indic;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.inline.InlinePresentationSpec;

import androidx.annotation.RequiresApi;
import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.common.ImageViewStyle;
import androidx.autofill.inline.common.TextViewStyle;
import androidx.autofill.inline.common.ViewStyle;
import androidx.autofill.inline.v1.InlineSuggestionUi;

import com.android.inputmethod.latin.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@RequiresApi(api = Build.VERSION_CODES.R)
public final class InlineAutofillUtils {

    private static final int MAX_SUGGESTION_COUNT = 6;
    private static final int CHIP_HEIGHT_DP = 32;
    private static final int CHIP_MIN_WIDTH_DP = 48;
    private static final int CHIP_MAX_WIDTH_DP = 300;

    private InlineAutofillUtils() {
        // Not instantiable.
    }

    public static InlineSuggestionsRequest createRequest(final Context context) {
        final int chipColor = resolveColor(context, R.attr.md3KeyColor, 0xFFECEDF1);
        final int titleColor = resolveColor(context, R.attr.md3OnSurface, 0xFF1B1B21);
        final int subtitleColor = resolveColor(context, R.attr.md3OnSurfaceVariant, 0xFF46464F);

        final Icon chipBackground = Icon.createWithResource(context,
                R.drawable.inline_autofill_chip_background);
        chipBackground.setTint(chipColor);
        final ViewStyle chipStyle = new ViewStyle.Builder()
                .setBackground(chipBackground)
                .setPadding(dp(context, 12), 0, dp(context, 12), 0)
                .build();
        final InlineSuggestionUi.Style style = InlineSuggestionUi.newStyleBuilder()
                .setChipStyle(chipStyle)
                .setSingleIconChipStyle(chipStyle)
                .setTitleStyle(new TextViewStyle.Builder()
                        .setTextColor(titleColor)
                        .setTextSize(14)
                        .build())
                .setSubtitleStyle(new TextViewStyle.Builder()
                        .setTextColor(subtitleColor)
                        .setTextSize(12)
                        .build())
                .setStartIconStyle(new ImageViewStyle.Builder()
                        .setTintList(ColorStateList.valueOf(subtitleColor))
                        .build())
                .setEndIconStyle(new ImageViewStyle.Builder()
                        .setTintList(ColorStateList.valueOf(subtitleColor))
                        .build())
                .build();
        final UiVersions.StylesBuilder styles = UiVersions.newStylesBuilder();
        styles.addStyle(style);
        final Bundle stylesBundle = styles.build();

        final InlinePresentationSpec spec = new InlinePresentationSpec.Builder(
                new Size(dp(context, CHIP_MIN_WIDTH_DP), dp(context, CHIP_HEIGHT_DP)),
                new Size(dp(context, CHIP_MAX_WIDTH_DP), dp(context, CHIP_HEIGHT_DP)))
                .setStyle(stylesBundle)
                .build();
        return new InlineSuggestionsRequest.Builder(Collections.singletonList(spec))
                .setMaxSuggestionCount(MAX_SUGGESTION_COUNT)
                .build();
    }

    /**
     * Inflates every suggestion and hands the surviving views to {@code onViewsReady} on the
     * main thread, preserving the response order. Suggestion content stays invisible to the
     * IME; each view is rendered by the autofill service in its own surface.
     */
    public static void inflate(final List<InlineSuggestion> suggestions, final Context context,
            final Consumer<ArrayList<View>> onViewsReady) {
        final int count = suggestions.size();
        final View[] views = new View[count];
        final AtomicInteger pending = new AtomicInteger(count);
        // WRAP_CONTENT lets each chip size to its text within the spec's min/max bounds.
        final Size size = new Size(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        for (int i = 0; i < count; i++) {
            final int index = i;
            suggestions.get(i).inflate(context, size, context.getMainExecutor(), view -> {
                views[index] = view;
                if (pending.decrementAndGet() == 0) {
                    final ArrayList<View> inflated = new ArrayList<>(count);
                    for (final View v : views) {
                        if (v != null) {
                            inflated.add(v);
                        }
                    }
                    onViewsReady.accept(inflated);
                }
            });
        }
    }

    private static int resolveColor(final Context context, final int attr,
            final int defaultColor) {
        final TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)
                && value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return defaultColor;
    }

    private static int dp(final Context context, final int dp) {
        return (int) (context.getResources().getDisplayMetrics().density * dp);
    }
}
