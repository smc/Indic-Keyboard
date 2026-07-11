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

package com.android.inputmethod.keyboard;

import android.content.Context;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.RichInputMethodSubtype;
import com.android.inputmethod.latin.utils.ResourceUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

/**
 * Non-interactive rendering of a keyboard for settings previews: the alphabet element of a
 * subtype, drawn with a keyboard theme's real styles at whatever width the view is laid out at.
 * Height follows the device keyboard's aspect ratio.
 */
public final class KeyboardPreviewView extends KeyboardView {
    private static final String TAG = KeyboardPreviewView.class.getSimpleName();

    private RichInputMethodSubtype mSubtype;
    private int mBuiltWidth;
    private int mBuiltHeight;

    public static KeyboardPreviewView create(final Context context, final KeyboardTheme theme,
            final InputMethodSubtype subtype) {
        SubtypeLocaleUtils.init(context);
        final KeyboardPreviewView view = new KeyboardPreviewView(
                new ContextThemeWrapper(context, theme.mStyleId));
        view.mSubtype = new RichInputMethodSubtype(subtype);
        return view;
    }

    private KeyboardPreviewView(final Context context) {
        super(context, null);
        setEnabled(false);
    }

    public void setSubtype(final InputMethodSubtype subtype) {
        mSubtype = new RichInputMethodSubtype(subtype);
        mBuiltWidth = 0;
        mBuiltHeight = 0;
        requestLayout();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int deviceContentWidth = ResourceUtils.getKeyboardContentWidth(getContext());
        final int contentWidth = Math.min(width, deviceContentWidth);
        final float aspectRatio = ResourceUtils.getDefaultKeyboardHeight(getResources())
                / (float) deviceContentWidth;
        final int contentHeight = Math.max(0, Math.round(contentWidth * aspectRatio));
        setMeasuredDimension(width, contentHeight);
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Wider than the real keyboard's content cap: keep keys at their real size and center
        // them in side gutters, like the actual tablet keyboard.
        final int gutter = Math.max(0,
                (w - ResourceUtils.getKeyboardContentWidth(getContext())) / 2);
        if (gutter != getPaddingLeft()) {
            setPadding(gutter, 0, gutter, 0);
        }
        buildKeyboardIfNeeded();
    }

    @Override
    protected float getKeyIconScale() {
        return mBuiltWidth <= 0 ? 1.0f
                : Math.min(1.0f, mBuiltWidth
                        / (float) ResourceUtils.getKeyboardContentWidth(getContext()));
    }

    private void buildKeyboardIfNeeded() {
        final int width = getWidth() - getPaddingLeft() - getPaddingRight();
        final int height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (mSubtype == null || width <= 0 || height <= 0
                || (width == mBuiltWidth && height == mBuiltHeight)) {
            return;
        }
        final KeyboardLayoutSet.Builder builder =
                new KeyboardLayoutSet.Builder(getContext(), null /* editorInfo */);
        builder.setKeyboardGeometry(width, height)
                .setSubtype(mSubtype)
                .disableKeyboardCache();
        try {
            setKeyboard(builder.build().getKeyboard(KeyboardId.ELEMENT_ALPHABET));
            mBuiltWidth = width;
            mBuiltHeight = height;
        } catch (final KeyboardLayoutSet.KeyboardLayoutSetException e) {
            Log.e(TAG, "Failed to build preview keyboard for "
                    + mSubtype.getKeyboardLayoutSetName(), e);
        }
    }
}
