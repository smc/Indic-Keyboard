/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.KeyboardAccessibilityDelegate;
import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.internal.KeyDrawParams;
import com.android.inputmethod.keyboard.internal.MoreKeySpec;
import com.android.inputmethod.latin.R;

/**
 * This is an extended {@link KeyboardView} class that hosts an emoji page keyboard.
 * Multi-touch unsupported. No gesture support.
 */
// TODO: Implement key popup preview.
final class EmojiPageKeyboardView extends KeyboardView implements
        GestureDetector.OnGestureListener {
    private static final long KEY_PRESS_DELAY_TIME = 250;  // msec
    private static final long KEY_RELEASE_DELAY_TIME = 30;  // msec

    public interface OnKeyEventListener {
        public void onPressKey(Key key);
        public void onReleaseKey(Key key);
        public void onHoldKey(Key key);
        public boolean isRecentsTab();
        public void onPickEmojiVariation(Key baseKey, String emoji);
    }

    private static final OnKeyEventListener EMPTY_LISTENER = new OnKeyEventListener() {
        @Override
        public void onPressKey(final Key key) {}
        @Override
        public void onReleaseKey(final Key key) {}
        @Override
        public void onHoldKey(final Key key) {}
        @Override
        public boolean isRecentsTab() { return false; }
        @Override
        public void onPickEmojiVariation(final Key baseKey, final String emoji) {}
    };

    private OnKeyEventListener mListener = EMPTY_LISTENER;
    private final KeyDetector mKeyDetector = new KeyDetector();
    private final GestureDetector mGestureDetector;
    private KeyboardAccessibilityDelegate<EmojiPageKeyboardView> mAccessibilityDelegate;

    private PopupWindow mVariationsPopup;
    private final Paint mTrianglePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mTrianglePath = new Path();

    public EmojiPageKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public EmojiPageKeyboardView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
        mGestureDetector = new GestureDetector(context, this);
        mGestureDetector.setIsLongpressEnabled(true /* isLongpressEnabled */);
        mHandler = new Handler();
        mTrianglePaint.setStyle(Paint.Style.FILL);
    }

    public void setOnKeyEventListener(final OnKeyEventListener listener) {
        mListener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKeyboard(final Keyboard keyboard) {
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(keyboard, 0 /* correctionX */, 0 /* correctionY */);
        if (AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            if (mAccessibilityDelegate == null) {
                mAccessibilityDelegate = new KeyboardAccessibilityDelegate<>(this, mKeyDetector);
            }
            mAccessibilityDelegate.setKeyboard(keyboard);
        } else {
            mAccessibilityDelegate = null;
        }
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard instanceof DynamicGridKeyboard) {
            final int width = keyboard.mOccupiedWidth + getPaddingLeft() + getPaddingRight();
            final int height = ((DynamicGridKeyboard) keyboard).getContentHeight()
                    + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(width, height);
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
        // Don't populate accessibility event with all Emoji keys.
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onHoverEvent(final MotionEvent event) {
        final KeyboardAccessibilityDelegate<EmojiPageKeyboardView> accessibilityDelegate =
                mAccessibilityDelegate;
        if (accessibilityDelegate != null
                && AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            return accessibilityDelegate.onHoverEvent(event);
        }
        return super.onHoverEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(final MotionEvent e) {
        if (mGestureDetector.onTouchEvent(e)) {
            return true;
        }
        final Key key = getKey(e);
        if (key != null && key != mCurrentKey) {
            releaseCurrentKey(false /* withKeyRegistering */);
        }
        return true;
    }

    // {@link GestureEnabler#OnGestureListener} methods.
    private Key mCurrentKey;
    private Runnable mPendingKeyDown;
    private boolean mHoldKey;
    private final Handler mHandler;

    private Key getKey(final MotionEvent e) {
        final int index = e.getActionIndex();
        final int x = (int)e.getX(index);
        final int y = (int)e.getY(index);
        return mKeyDetector.detectHitKey(x, y);
    }

    void callListenerOnReleaseKey(final Key releasedKey, final boolean withKeyRegistering) {
        releasedKey.onReleased();
        invalidateKey(releasedKey);
        if (withKeyRegistering) {
            mListener.onReleaseKey(releasedKey);
        }
    }

    void callListenerOnPressKey(final Key pressedKey) {
        mPendingKeyDown = null;
        pressedKey.onPressed();
        invalidateKey(pressedKey);
        mListener.onPressKey(pressedKey);
    }

    public void releaseCurrentKey(final boolean withKeyRegistering) {
        if (mHoldKey) {
            mHoldKey = false;
            return;
        }
        mHandler.removeCallbacks(mPendingKeyDown);
        mPendingKeyDown = null;
        final Key currentKey = mCurrentKey;
        if (currentKey == null) {
            return;
        }
        callListenerOnReleaseKey(currentKey, withKeyRegistering);
        mCurrentKey = null;
    }

    public void callListenerOnHoldKey(final Key key) {
        mHoldKey = true;
        key.onReleased();
        invalidateKey(key);
        mListener.onHoldKey(key);
    }

    @Override
    public boolean onDown(final MotionEvent e) {
        final Key key = getKey(e);
        releaseCurrentKey(false /* withKeyRegistering */);
        mCurrentKey = key;
        if (key == null) {
            return false;
        }
        // Do not trigger key-down effect right now in case this is actually a fling action.
        mPendingKeyDown = new Runnable() {
            @Override
            public void run() {
                callListenerOnPressKey(key);
            }
        };
        mHandler.postDelayed(mPendingKeyDown, KEY_PRESS_DELAY_TIME);
        return false;
    }

    @Override
    public void onShowPress(final MotionEvent e) {
        // User feedback is done at {@link #onDown(MotionEvent)}.
    }

    @Override
    public boolean onSingleTapUp(final MotionEvent e) {
        final Key key = getKey(e);
        final Runnable pendingKeyDown = mPendingKeyDown;
        final Key currentKey = mCurrentKey;
        releaseCurrentKey(false /* withKeyRegistering */);
        if (key == null) {
            return false;
        }
        if (key == currentKey && pendingKeyDown != null) {
            pendingKeyDown.run();
            // Trigger key-release event a little later so that a user can see visual feedback.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    callListenerOnReleaseKey(key, true /* withRegistering */);
                }
            }, KEY_RELEASE_DELAY_TIME);
        } else {
            callListenerOnReleaseKey(key, true /* withRegistering */);
        }
        return true;
    }

    @Override
    public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX,
           final float distanceY) {
        dismissVariationsPopup();
        releaseCurrentKey(false /* withKeyRegistering */);
        return false;
    }

    @Override
    public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX,
            final float velocityY) {
        releaseCurrentKey(false /* withKeyRegistering */);
        return false;
    }

    @Override
    public void onLongPress(final MotionEvent e) {
        final Key key = getKey(e);
        if (key == null) {
            return;
        }
        if (!mListener.isRecentsTab() && key.getMoreKeys() != null) {
            mHoldKey = true;
            key.onReleased();
            invalidateKey(key);
            showVariationsPopup(key);
            return;
        }
        callListenerOnHoldKey(key);
    }

    private void showVariationsPopup(final Key key) {
        dismissVariationsPopup();
        final Context context = getContext();
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.md3_popup_background);
        row.setElevation(dp(6));

        final int cellWidth = key.getWidth();
        final int cellHeight = key.getHeight();
        for (final MoreKeySpec spec : key.getMoreKeys()) {
            final String emoji = (spec.mOutputText != null) ? spec.mOutputText : spec.mLabel;
            final TextView cell = new TextView(context);
            cell.setText(emoji);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(TypedValue.COMPLEX_UNIT_PX, cellHeight * 0.5f);
            cell.setWidth(cellWidth);
            cell.setHeight(cellHeight);
            cell.setClickable(true);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    mListener.onPickEmojiVariation(key, emoji);
                    dismissVariationsPopup();
                }
            });
            row.addView(cell);
        }

        final int shadowPad = dp(12);
        final FrameLayout container = new FrameLayout(context);
        container.setPadding(shadowPad, shadowPad, shadowPad, shadowPad);
        container.setClipToPadding(false);
        container.setClipChildren(false);
        container.addView(row);

        final PopupWindow popup = new PopupWindow(container,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mVariationsPopup = popup;

        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        final int popupWidth = container.getMeasuredWidth();
        final int popupHeight = container.getMeasuredHeight();
        final int[] location = new int[2];
        getLocationInWindow(location);
        final int x = location[0] + key.getX() + key.getWidth() / 2 - popupWidth / 2;
        // Sit the box just above the key (the container's bottom padding holds the shadow).
        final int y = location[1] + key.getY() - popupHeight + shadowPad;
        popup.showAtLocation(this, Gravity.NO_GRAVITY, Math.max(0, x), Math.max(0, y));
        dimBehind(popup);
    }

    private void dimBehind(final PopupWindow popup) {
        final View decor = popup.getContentView().getRootView();
        if (decor == null
                || !(decor.getLayoutParams() instanceof WindowManager.LayoutParams)) {
            return;
        }
        final WindowManager wm =
                (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        final WindowManager.LayoutParams lp = (WindowManager.LayoutParams) decor.getLayoutParams();
        lp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        lp.dimAmount = 0.32f;
        wm.updateViewLayout(decor, lp);
    }

    private void dismissVariationsPopup() {
        if (mVariationsPopup != null) {
            if (mVariationsPopup.isShowing()) {
                mVariationsPopup.dismiss();
            }
            mVariationsPopup = null;
        }
    }

    private int dp(final int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDrawKeyTopVisuals(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
        super.onDrawKeyTopVisuals(key, canvas, paint, params);
        if (key.getMoreKeys() == null) {
            return;
        }
        final int width = key.getDrawWidth();
        final float side = dp(6);
        final float inset = dp(2);
        mTrianglePath.reset();
        mTrianglePath.moveTo(width - inset, inset);
        mTrianglePath.lineTo(width - inset - side, inset);
        mTrianglePath.lineTo(width - inset, inset + side);
        mTrianglePath.close();
        mTrianglePaint.setColor(params.mHintLabelColor);
        canvas.drawPath(mTrianglePath, mTrianglePaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissVariationsPopup();
        super.onDetachedFromWindow();
    }
}
