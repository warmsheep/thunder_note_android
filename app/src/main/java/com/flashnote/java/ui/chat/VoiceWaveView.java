package com.flashnote.java.ui.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.flashnote.java.R;

public class VoiceWaveView extends View {
    
    private static final int BAR_COUNT = 12;
    private static final float CORNER_RADIUS = 3f;
    private static final float BAR_SPACING_RATIO = 0.15f;
    private static final int ANIMATION_DURATION = 600;
    
    private Paint barPaint;
    private Paint backgroundPaint;
    private float[] barHeights;
    private float[] targetHeights;
    private ValueAnimator[] animators;
    private boolean isPlaying = false;
    
    private int barColor;
    
    public VoiceWaveView(Context context) {
        super(context);
        init(context);
    }
    
    public VoiceWaveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public VoiceWaveView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        barColor = ContextCompat.getColor(context, R.color.primary);
        
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(barColor);
        barPaint.setStyle(Paint.Style.FILL);
        
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(0x1A000000);
        backgroundPaint.setStyle(Paint.Style.FILL);
        
        barHeights = new float[BAR_COUNT];
        targetHeights = new float[BAR_COUNT];
        animators = new ValueAnimator[BAR_COUNT];
        
        for (int i = 0; i < BAR_COUNT; i++) {
            float restingHeight = getRestingHeight(i);
            barHeights[i] = restingHeight;
            targetHeights[i] = restingHeight;
        }
    }
    
    public void setBarColor(int color) {
        this.barColor = color;
        barPaint.setColor(color);
        invalidate();
    }
    
    public void startAnimation() {
        if (isPlaying) return;
        isPlaying = true;
        
        for (int i = 0; i < BAR_COUNT; i++) {
            startBarAnimation(i);
        }
    }
    
    public void stopAnimation() {
        isPlaying = false;
        
        for (int i = 0; i < BAR_COUNT; i++) {
            if (animators[i] != null) {
                animators[i].cancel();
                animators[i] = null;
            }
        }
        
        for (int i = 0; i < BAR_COUNT; i++) {
            float restingHeight = getRestingHeight(i);
            barHeights[i] = restingHeight;
            targetHeights[i] = restingHeight;
        }
        invalidate();
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    private void startBarAnimation(final int index) {
        if (animators[index] != null) {
            animators[index].cancel();
        }
        
        float centerBias = getCenterBias(index);
        float minTarget = 0.22f + 0.22f * centerBias;
        float maxTarget = 0.55f + 0.45f * centerBias;
        final float newTarget = minTarget + (float) Math.random() * (maxTarget - minTarget);
        targetHeights[index] = newTarget;
        
        int duration = ANIMATION_DURATION + (int) (Math.random() * 220) - (int) (120f * centerBias);
        duration = Math.max(320, duration);
        
        ValueAnimator animator = ValueAnimator.ofFloat(barHeights[index], newTarget);
        animators[index] = animator;
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            barHeights[index] = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isPlaying && animators[index] == animation) {
                    startBarAnimation(index);
                }
            }
        });
        
        postDelayed(() -> {
            if (isPlaying && animators[index] == animator) {
                animator.start();
            }
        }, index * 35L);
    }

    private float getCenterBias(int index) {
        float center = (BAR_COUNT - 1) / 2f;
        float normalizedDistance = Math.abs(index - center) / center;
        return Math.max(0f, 1f - normalizedDistance);
    }

    private float getRestingHeight(int index) {
        return 0.3f + 0.3f * getCenterBias(index);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        if (width == 0 || height == 0) return;
        
        float totalSpacing = width * BAR_SPACING_RATIO;
        float barWidth = (width - totalSpacing) / BAR_COUNT;
        float spacing = totalSpacing / (BAR_COUNT - 1);
        
        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * (barWidth + spacing);
            float barHeight = height * barHeights[i];
            
            float top = (height - barHeight) / 2;
            float right = left + barWidth;
            float bottom = top + barHeight;
            
            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, barPaint);
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }
}
