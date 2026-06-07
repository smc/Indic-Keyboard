/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.android.inputmethod.latin.common.CoordinateUtils;

import java.util.ArrayList;

public final class DrawingPreviewPlacerView extends RelativeLayout {
    private final int[] mKeyboardViewOrigin = CoordinateUtils.newInstance();
    private int mKeyboardViewWidth;
    private int mKeyboardViewHeight;
    private boolean mScrimVisible;
    private int mScrimColor;
    private final Paint mScrimPaint = new Paint();

    private final ArrayList<AbstractDrawingPreview> mPreviews = new ArrayList<>();

    public DrawingPreviewPlacerView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        // Let an elevated more-keys panel cast its shadow past the panel's own bounds instead of
        // being clipped to them.
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setScrim(final boolean visible, final int color) {
        if (mScrimVisible == visible && mScrimColor == color) {
            return;
        }
        mScrimVisible = visible;
        mScrimColor = color;
        invalidate();
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        if (!enabled) return;
        final Paint layerPaint = new Paint();
        layerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        setLayerType(LAYER_TYPE_HARDWARE, layerPaint);
    }

    public void addPreview(final AbstractDrawingPreview preview) {
        if (mPreviews.indexOf(preview) < 0) {
            mPreviews.add(preview);
        }
    }

    public void setKeyboardViewGeometry(final int[] originCoords, final int width,
            final int height) {
        CoordinateUtils.copy(mKeyboardViewOrigin, originCoords);
        mKeyboardViewWidth = width;
        mKeyboardViewHeight = height;
        final int count = mPreviews.size();
        for (int i = 0; i < count; i++) {
            mPreviews.get(i).setKeyboardViewGeometry(originCoords, width, height);
        }
    }

    public void deallocateMemory() {
        final int count = mPreviews.size();
        for (int i = 0; i < count; i++) {
            mPreviews.get(i).onDeallocateMemory();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        deallocateMemory();
    }

    @Override
    public void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        final int originX = CoordinateUtils.x(mKeyboardViewOrigin);
        final int originY = CoordinateUtils.y(mKeyboardViewOrigin);
        canvas.translate(originX, originY);
        if (mScrimVisible) {
            mScrimPaint.setColor(mScrimColor);
            canvas.drawRect(0, 0, mKeyboardViewWidth, mKeyboardViewHeight, mScrimPaint);
        }
        final int count = mPreviews.size();
        for (int i = 0; i < count; i++) {
            mPreviews.get(i).drawPreview(canvas);
        }
        canvas.translate(-originX, -originY);
    }
}
