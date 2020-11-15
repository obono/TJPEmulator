/*
 * Copyright (C) 2020 OBONO
 * https://obono.hateblo.jp/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.obnsoft.tjpemu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

public class EmulatorScreenView extends View {

    private static final int SKIN_W = 144;
    private static final int SKIN_H = 144;

    private static final int SCREEN_X = 8;
    private static final int SCREEN_Y = 32;
    private static final int SCREEN_W = TJPEmulator.SCREEN_WIDTH;
    private static final int SCREEN_H = TJPEmulator.SCREEN_HEIGHT;

    private static final int BUTTON_DPAD_G  = 30;
    private static final int BUTTON_A_GX    = 8;
    private static final int BUTTON_A_GY    = 8;
    private static final int BUTTON_SIZE    = 20;
    private static final int BUTTON_COLOR_ON  = Color.argb(224, 192, 192, 128);
    private static final int BUTTON_COLOR_OFF = Color.argb(160, 192, 192, 192);

    private static final int TOUCH_STATE_MAX = 10;

    private float       mBaseX, mBaseY, mScale;
    private DrawObject  mSkin;
    private DrawObject  mScreen;
    private Paint       mButtonPaint;

    private boolean[]   mButtonState = new boolean[Native.BUTTON_MAX];
    private PointF[]    mButtonPosition = new PointF[Native.BUTTON_MAX];
    private float       mButtonSize;
    private PointF[]    mTouchPoint = new PointF[TOUCH_STATE_MAX];
    private int         mTouchPointCount;

    /*-----------------------------------------------------------------------*/

    class DrawObject {

        public Bitmap bitmap;
        public Matrix matrix;
        public Paint  paint;

        public DrawObject(int drawableId, boolean isOnlyAlpha) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), drawableId);
            this.matrix = new Matrix();
            this.paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            if (isOnlyAlpha) {
                this.bitmap = bitmap.extractAlpha();
                this.paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
                bitmap.recycle();
            } else {
                this.bitmap = bitmap;
            }
        }

        public DrawObject(Bitmap bitmap, Matrix matrix, Paint paint) {
            this.bitmap = bitmap;
            this.matrix = (matrix == null) ? new Matrix() : matrix;
            this.paint  = (paint  == null) ? new Paint()  : paint;
        }

        public void setCoords(float x, float y, float w, float h) {
            matrix.setScale(mScale * w / bitmap.getWidth(), mScale * h / bitmap.getHeight());
            matrix.postTranslate(mBaseX + x * mScale, mBaseY + y * mScale);
        }

        public void setCoordsCenter(float x, float y, float w, float h) {
            setCoords(x - w / 2f, y - h / 2f, w, h);
        }

        public synchronized void draw(Canvas canvas) {
            if (!bitmap.isRecycled()) {
                canvas.drawBitmap(bitmap, matrix, paint);
            }
        }

        public synchronized void recycle() {
            bitmap.recycle();
        }
    }

    /*-----------------------------------------------------------------------*/

    public EmulatorScreenView(Context context) {
        this(context, null);
    }

    public EmulatorScreenView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmulatorScreenView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setFocusable(false);

        mSkin = new DrawObject(R.drawable.ssd1306, false);
        Paint screenPaint = new Paint(0); // No ANTI_ALIAS_FLAG, No FILTER_BITMAP_FLAG
        screenPaint.setFilterBitmap(false);
        mScreen = new DrawObject(Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888),
                null, screenPaint);
        mButtonPaint = new Paint();
        mButtonPaint.setAntiAlias(true);
        mButtonPaint.setStyle(Paint.Style.FILL);

        for (int buttonIdx = 0; buttonIdx < Native.BUTTON_MAX; buttonIdx++) {
            mButtonPosition[buttonIdx] = new PointF();
        }
        for (int touchIdx = 0; touchIdx < TOUCH_STATE_MAX; touchIdx++) {
            mTouchPoint[touchIdx] = new PointF();
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        mTouchPointCount = (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                ? 0 : event.getPointerCount();
        for (int touchIdx = 0; touchIdx < mTouchPointCount; touchIdx++) {
            mTouchPoint[touchIdx].set(event.getX(touchIdx), event.getY(touchIdx));
        }
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        /*  Skin position  */
        boolean isLandscape = (w > h);
        int tmpH = isLandscape ? SKIN_H : SKIN_H * 4 / 3;
        mScale = Math.max(Math.min(w / SKIN_W, h / tmpH), 1);
        mBaseX = (w - SKIN_W * mScale) / 2f;
        mBaseY = (h - tmpH * mScale) / 2f;
        mSkin.setCoords(0, 0, SKIN_W, SKIN_H);
        mScreen.setCoords(SCREEN_X, SCREEN_Y, SCREEN_W, SCREEN_H);

        /*  Buttons position  */
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        float buttonScale = displayMetrics.density * 2f;
        float dpadX, dpadY, aX, aY;
        float dpadGap = BUTTON_DPAD_G * buttonScale;
        mButtonSize = BUTTON_SIZE * buttonScale;
        dpadX = (BUTTON_SIZE + BUTTON_DPAD_G) * buttonScale;
        dpadY = h - dpadX;
        aX = w - (BUTTON_SIZE + BUTTON_A_GX) * buttonScale;
        aY = h - (BUTTON_SIZE + BUTTON_A_GY) * buttonScale;
        mButtonPosition[Native.BUTTON_UP   ].set(dpadX, dpadY - dpadGap);
        mButtonPosition[Native.BUTTON_DOWN ].set(dpadX, dpadY + dpadGap);
        mButtonPosition[Native.BUTTON_LEFT ].set(dpadX - dpadGap, dpadY);
        mButtonPosition[Native.BUTTON_RIGHT].set(dpadX + dpadGap, dpadY);
        mButtonPosition[Native.BUTTON_A    ].set(aX, aY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        /*  OLED  */
        mSkin.draw(canvas);
        mScreen.draw(canvas);

        /*  Buttons  */
        for (int buttonIdx = 0; buttonIdx < Native.BUTTON_MAX; buttonIdx++) {
            mButtonPaint.setColor(mButtonState[buttonIdx] ? BUTTON_COLOR_ON : BUTTON_COLOR_OFF);
            canvas.drawCircle(mButtonPosition[buttonIdx].x, mButtonPosition[buttonIdx].y,
                    mButtonSize, mButtonPaint);
        }
    }

    /*-----------------------------------------------------------------------*/

    public boolean[] updateButtonState() {
        for (int buttonIdx = 0; buttonIdx < Native.BUTTON_MAX; buttonIdx++) {
            mButtonState[buttonIdx] = false;
        }
        float threshold = mButtonSize * 1.25f;
        for (int touchIdx = 0; touchIdx < mTouchPointCount; touchIdx++) {
            for (int buttonIdx = 0; buttonIdx < Native.BUTTON_MAX; buttonIdx++) {
                if (PointF.length(mTouchPoint[touchIdx].x - mButtonPosition[buttonIdx].x,
                        mTouchPoint[touchIdx].y - mButtonPosition[buttonIdx].y) <= threshold) {
                    mButtonState[buttonIdx] = true;
                }
            }
        }
        return mButtonState;
    }

    public void updateScreen(int[] pixels) {
        synchronized (mScreen) {
            if (!mScreen.bitmap.isRecycled()) {
                mScreen.bitmap.setPixels(pixels, 0, SCREEN_W, 0, 0, SCREEN_W, SCREEN_H);
            }
        }
    }

    public void onDestroy() {
        mSkin.recycle();
        mScreen.recycle();
    }

}
