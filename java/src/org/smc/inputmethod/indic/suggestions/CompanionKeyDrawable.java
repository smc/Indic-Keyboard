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

package org.smc.inputmethod.indic.suggestions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.TypedValue;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.KeyboardLanguages;

/** Speech-bubble icon for the toolbox companion key, rendered from
 *  companion_mode_{on,off}.svg geometry with the companion language's own glyph. */
public final class CompanionKeyDrawable extends Drawable {
    private static final float VIEWPORT = 24f;
    private static final float ZOOM = 1.3f;
    private static final float CONTENT_CENTER_X = 12f;
    private static final float CONTENT_CENTER_Y = 11.7f;
    // Shown while no companion language is configured.
    private static final String FALLBACK_GLYPH = "अ";

    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path mBubble = new Path();

    private final Context mContext;
    private String mGlyph = FALLBACK_GLYPH;
    private boolean mOn = true;

    public CompanionKeyDrawable(final Context context) {
        mContext = context;
        final int ink = resolveInk(context);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        mStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        mStrokePaint.setColor(ink);
        mGapPaint.setStyle(Paint.Style.STROKE);
        mGapPaint.setStrokeCap(Paint.Cap.ROUND);
        mGapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mTextPaint.setColor(ink);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        buildBubblePath();
    }

    private static int resolveInk(final Context context) {
        final TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.md3OnSurfaceVariant, value, true)
                && value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return 0xFF5F6368;
    }

    private void buildBubblePath() {
        mBubble.moveTo(6f, 4f);
        mBubble.lineTo(18f, 4f);
        mBubble.arcTo(new RectF(15f, 4f, 21f, 10f), 270f, 90f);
        mBubble.lineTo(21f, 13f);
        mBubble.arcTo(new RectF(15f, 10f, 21f, 16f), 0f, 90f);
        mBubble.lineTo(13.9f, 16f);
        mBubble.lineTo(12f, 18.7f);
        mBubble.lineTo(10.1f, 16f);
        mBubble.lineTo(6f, 16f);
        mBubble.arcTo(new RectF(3f, 10f, 9f, 16f), 90f, 90f);
        mBubble.lineTo(3f, 7f);
        mBubble.arcTo(new RectF(3f, 4f, 9f, 10f), 180f, 90f);
        mBubble.close();
    }

    private String glyphFor(final String langCode) {
        final String glyph = KeyboardLanguages.glyphForLanguage(mContext, langCode);
        return glyph != null ? glyph : FALLBACK_GLYPH;
    }

    public void setState(final String langCode, final boolean on) {
        final String glyph = glyphFor(langCode);
        if (glyph.equals(mGlyph) && on == mOn) {
            return;
        }
        mGlyph = glyph;
        mOn = on;
        invalidateSelf();
    }

    @Override
    public void draw(final Canvas canvas) {
        final RectF bounds = new RectF(getBounds());
        if (bounds.isEmpty()) {
            return;
        }
        final int layer = canvas.saveLayer(bounds, null);
        // The SVG leaves wide margins in its viewport while the sibling Material icons fill
        // theirs, so zoom onto the artwork's own center to match their visual size.
        final float scale = Math.min(bounds.width(), bounds.height()) / VIEWPORT * ZOOM;
        canvas.translate(bounds.centerX() - CONTENT_CENTER_X * scale,
                bounds.centerY() - CONTENT_CENTER_Y * scale);
        canvas.scale(scale, scale);

        mStrokePaint.setStrokeWidth(1.7f);
        canvas.drawPath(mBubble, mStrokePaint);
        mTextPaint.setTextSize(9.4f);
        canvas.drawText(mGlyph, 12f, 13.4f, mTextPaint);
        if (!mOn) {
            mGapPaint.setStrokeWidth(3.6f);
            canvas.drawLine(4.6f, 19.4f, 19.4f, 4.6f, mGapPaint);
            mStrokePaint.setStrokeWidth(1.8f);
            canvas.drawLine(4.6f, 19.4f, 19.4f, 4.6f, mStrokePaint);
        }
        canvas.restoreToCount(layer);
    }

    @Override
    public void setAlpha(final int alpha) {
        mStrokePaint.setAlpha(alpha);
        mTextPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(final ColorFilter colorFilter) {
        mStrokePaint.setColorFilter(colorFilter);
        mTextPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
