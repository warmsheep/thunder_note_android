package com.flashnote.java.ui.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.flashnote.java.R;

/**
 * Custom View that displays animated waveform bars for voice recording visualization.
 * Shows 20-30 vertical bars with heights animated based on audio amplitude.
 */
public class RecordingWaveView extends View {

    private static final int BAR_COUNT = 25;
    private static final float BAR_WIDTH_RATIO = 0.6f;
    private static final float MIN_BAR_HEIGHT_RATIO = 0.15f;
    private static final float CORNER_RADIUS = 4f;
    private static final int ANIMATION_INTERVAL = 80;

    private Paint activePaint;
    private Paint inactivePaint;
    private float[] barHeights;
    private float[] targetHeights;
    private float currentAmplitude = 0f;
    private boolean isAnimating = false;
    private Runnable animationRunnable;
    
    private int primaryColor;
    private int inactiveColor;

    public RecordingWaveView(Context context) {
        super(context);
        init(context);
    }

    public RecordingWaveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RecordingWaveView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        primaryColor = context.getColor(R.color.primary);
        inactiveColor = context.getColor(R.color.text_hint);

        activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setColor(primaryColor);
        activePaint.setStyle(Paint.Style.FILL);

        inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactivePaint.setColor(inactiveColor);
        inactivePaint.setStyle(Paint.Style.FILL);

        barHeights = new float[BAR_COUNT];
        targetHeights = new float[BAR_COUNT];
        
        // Initialize with minimum height
        for (int i = 0; i < BAR_COUNT; i++) {
            barHeights[i] = MIN_BAR_HEIGHT_RATIO;
            targetHeights[i] = MIN_BAR_HEIGHT_RATIO;
        }
    }

    /**
     * Start the waveform animation.
     */
    public void startWave() {
        if (isAnimating) {
            return;
        }
        isAnimating = true;
        
        animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAnimating) {
                    return;
                }
                
                updateBarHeights();
                invalidate();
                
                postDelayed(this, ANIMATION_INTERVAL);
            }
        };
        
        post(animationRunnable);
    }

    /**
     * Stop the waveform animation.
     */
    public void stopWave() {
        isAnimating = false;
        
        if (animationRunnable != null) {
            removeCallbacks(animationRunnable);
            animationRunnable = null;
        }
        
        // Reset bars to minimum height
        for (int i = 0; i < BAR_COUNT; i++) {
            barHeights[i] = MIN_BAR_HEIGHT_RATIO;
            targetHeights[i] = MIN_BAR_HEIGHT_RATIO;
        }
        invalidate();
    }

    /**
     * Update the amplitude for the waveform visualization.
     * @param amplitude Normalized amplitude value between 0 and 1
     */
    public void updateAmplitude(float amplitude) {
        currentAmplitude = Math.max(0f, Math.min(1f, amplitude));
        
        // Generate target heights based on amplitude with some randomness
        for (int i = 0; i < BAR_COUNT; i++) {
            float randomFactor = 0.3f + (float) Math.random() * 0.7f;
            float baseHeight = MIN_BAR_HEIGHT_RATIO + (1f - MIN_BAR_HEIGHT_RATIO) * currentAmplitude * randomFactor;
            targetHeights[i] = Math.max(MIN_BAR_HEIGHT_RATIO, Math.min(1f, baseHeight));
        }
    }

    private void updateBarHeights() {
        // Smooth interpolation towards target heights
        float interpolation = 0.3f;
        
        for (int i = 0; i < BAR_COUNT; i++) {
            barHeights[i] = barHeights[i] + (targetHeights[i] - barHeights[i]) * interpolation;
            
            // Add slight random fluctuation when amplitude is high
            if (currentAmplitude > 0.1f && Math.random() > 0.7f) {
                float fluctuation = (float) (Math.random() - 0.5f) * 0.1f;
                barHeights[i] = Math.max(MIN_BAR_HEIGHT_RATIO, Math.min(1f, barHeights[i] + fluctuation));
            }
        }
        
        // Slowly decay target heights when amplitude drops
        if (currentAmplitude < 0.05f) {
            for (int i = 0; i < BAR_COUNT; i++) {
                targetHeights[i] = Math.max(MIN_BAR_HEIGHT_RATIO, targetHeights[i] * 0.95f);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        
        if (width == 0 || height == 0) {
            return;
        }

        float totalBarWidth = width / (float) BAR_COUNT;
        float barWidth = totalBarWidth * BAR_WIDTH_RATIO;
        float spacing = totalBarWidth - barWidth;

        // Calculate center Y position
        float centerY = height / 2f;
        
        // Draw each bar centered vertically
        RectF rect = new RectF();
        
        for (int i = 0; i < BAR_COUNT; i++) {
            float left = i * totalBarWidth + spacing / 2;
            float barHeight = barHeights[i] * height;
            float top = centerY - barHeight / 2;
            float right = left + barWidth;
            float bottom = centerY + barHeight / 2;
            
            rect.set(left, top, right, bottom);
            
            // Use active color if amplitude is significant, otherwise use inactive
            Paint paint = currentAmplitude > 0.05f ? activePaint : inactivePaint;
            canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopWave();
    }
}
