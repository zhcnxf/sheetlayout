/*
 * Copyright 2015 Taosha
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
 *
 */

package org.taosha.android.widget;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import org.taosha.android.widget.sheetlayout.R;

/**
 * NOTE: This layout will block horizontal scrollablity of it's child views.
 * Created by san on 6/2/15.
 */
public class SheetLayout extends FrameLayout {

    private float mMaximumVelocity;
    private float mTouchSlop;
    private int mCurrentChild;
    private int mFrontChild;
    private int mBackChild;
    private float mPercentage;
    private boolean mMoving;
    private boolean mAnimating;
    private float mInitialMotionX;
    private float mInitialMotionY;
    private VelocityTracker mVelocityTracker;
    private int mShaderColor;
    private float mMinScale;
    private Paint mShaderPaint = new Paint();
    private boolean mIsBeingDragged;
    private boolean mIntercepted;

    public SheetLayout(Context context) {
        super(context);
        init(context, null, 0, R.style.SheetLayout);
    }

    public SheetLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, R.style.SheetLayout);
    }

    public SheetLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, R.style.SheetLayout);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SheetLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private static int resolveShadeColor(int color, float alpha) {
        if (alpha > 1) {
            alpha = 1;
        } else if (alpha < 0) {
            alpha = 0;
        }
        int a = Color.alpha(color), r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.argb(Math.round(alpha * a), r, g, b);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SheetLayout, defStyleAttr, defStyleRes);
        mShaderColor = a.getColor(R.styleable.SheetLayout_sheetShaderColor, 0);
        mMinScale = a.getFloat(R.styleable.SheetLayout_sheetMinScale, 0.5f);
        a.recycle();
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mMoving | mAnimating) {
            int saveCount = canvas.save();
            float scale = 1 - (1 - mMinScale) * mPercentage;
            canvas.scale(scale, scale, canvas.getWidth() / 2f, canvas.getHeight() / 2f);
            View backChild = getChildAt(mBackChild);
            drawChild(canvas, getChildAt(mBackChild), getDrawingTime());
            final Paint shaderPaint = mShaderPaint;
            shaderPaint.setColor(resolveShadeColor(mShaderColor, mPercentage));
            canvas.drawRect(backChild.getLeft(), backChild.getTop(), backChild.getRight(), backChild.getBottom(), shaderPaint);
            canvas.restoreToCount(saveCount);

            saveCount = canvas.save();
            canvas.translate(canvas.getWidth() * (mPercentage - 1), 0);
            drawChild(canvas, getChildAt(mFrontChild), getDrawingTime());
            canvas.restoreToCount(saveCount);
        } else if (getChildCount() > 0) {
            drawChild(canvas, getChildAt(mCurrentChild), getDrawingTime());
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mIntercepted) {
            onTouchEvent(ev);
        } else {
            mIntercepted = onInterceptTouchEvent(ev);
        }

        View v = getChildAt(mCurrentChild);
        if (v != null) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                mIntercepted = false;
            }

            MotionEvent event = MotionEvent.obtain(ev);
            event.offsetLocation(-v.getLeft(), -v.getTop());
            if (!mIntercepted) {
                v.dispatchTouchEvent(event);
            } else {
                event.setAction(MotionEvent.ACTION_CANCEL);
                v.dispatchTouchEvent(event);
            }
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mIsBeingDragged)
            return true;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                final float x = ev.getX(), y = ev.getY();
                final float xDiff = Math.abs(x - mInitialMotionX);
                final float yDiff = Math.abs(y - mInitialMotionY);
                if (xDiff > mTouchSlop && xDiff * 0.5f > yDiff) {
                    mIsBeingDragged = true;
                    requestParentDisallowInterceptTouchEvent(true);
                }
                if (mIsBeingDragged) {
                    onUpdateDeltaX(ev.getX() - mInitialMotionX);
                }
                break;
            case MotionEvent.ACTION_DOWN:
                mInitialMotionX = ev.getX();
                mInitialMotionY = ev.getY();
                break;
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mAnimating) {
            return true;
        }

        if (getChildCount() == 0) {
            return false;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mInitialMotionX = ev.getX();
                break;
            case MotionEvent.ACTION_MOVE:
                onUpdateDeltaX(ev.getX() - mInitialMotionX);
                break;
            case MotionEvent.ACTION_UP: // fallthrough;
            case MotionEvent.ACTION_CANCEL:
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                onFinishDrag(ev.getX() - mInitialMotionX, mVelocityTracker.getXVelocity());
                if (mVelocityTracker != null) {
                    mIsBeingDragged = false;
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
        }
        return true;
    }

    private void onUpdateDeltaX(float deltaX) {
        if (deltaX > 0 && mCurrentChild > 0) {
            mMoving = true;
            mFrontChild = mCurrentChild - 1;
            mBackChild = mCurrentChild;
            mPercentage = deltaX / getWidth();
            invalidate();
        } else if (deltaX < 0 && mCurrentChild < getChildCount() - 1) {
            mMoving = true;
            mFrontChild = mCurrentChild;
            mBackChild = mCurrentChild + 1;
            mPercentage = 1 + (deltaX / getWidth());
            invalidate();
        }
    }

    private void onFinishDrag(float deltaX, float velocityX) {
        final float targetPercentage;
        if (velocityX > 2000) {
            targetPercentage = 1;
        } else if (velocityX < -2000) {
            targetPercentage = 0;
        } else if (mPercentage < 0.5) {
            targetPercentage = 0;
        } else {
            targetPercentage = 1;
        }
        final ObjectAnimator anim = ObjectAnimator.ofFloat(this, "percentage", mPercentage, targetPercentage)
                .setDuration(500);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addListener(new SimpleAnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (targetPercentage == 0) {
                    mCurrentChild = mBackChild;
                } else {
                    mCurrentChild = mFrontChild;
                }
                mMoving = mAnimating = false;
                invalidate();
            }
        });
        anim.start();
    }

    private void requestParentDisallowInterceptTouchEvent(boolean disallowIntercept) {
        final ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    public void setPercentage(float percentage) {
        mPercentage = percentage;
        invalidate();
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        return (direction < 0 && mCurrentChild > 0)
                || (direction > 0 && mCurrentChild < (getChildCount() - 1));
    }
}
