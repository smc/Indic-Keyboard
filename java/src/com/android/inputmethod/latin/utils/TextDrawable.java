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

package com.android.inputmethod.latin.utils;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import javax.annotation.Nonnull;

public final class TextDrawable extends Drawable {
    private final String mText;
    private final int mSize;
    private final Paint mTextPaint;
    private final Paint mBackgroundPaint;

    public TextDrawable(@Nonnull final String text, final int textColor, final int backgroundColor,
            final int sizePx) {
        mText = text;
        mSize = sizePx;
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(textColor);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTextSize(sizePx * 0.5f);
        mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBackgroundPaint.setColor(backgroundColor);
    }

    @Override
    public void draw(@Nonnull final Canvas canvas) {
        final Rect bounds = getBounds();
        final float radius = Math.min(bounds.width(), bounds.height()) / 2f;
        if (mBackgroundPaint.getAlpha() != 0) {
            canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius,
                    mBackgroundPaint);
        }

        final float maxWidth = radius * 1.4f;
        if (mTextPaint.measureText(mText) > maxWidth) {
            mTextPaint.setTextSize(mTextPaint.getTextSize() * maxWidth / mTextPaint.measureText(mText));
        }
        final Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        final float baseline = bounds.exactCenterY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(mText, bounds.exactCenterX(), baseline, mTextPaint);
    }

    @Override
    public int getIntrinsicWidth() {
        return mSize;
    }

    @Override
    public int getIntrinsicHeight() {
        return mSize;
    }

    @Override
    public void setAlpha(final int alpha) {
        mTextPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(final ColorFilter colorFilter) {
        mTextPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
