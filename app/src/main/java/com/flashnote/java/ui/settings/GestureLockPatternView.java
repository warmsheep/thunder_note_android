package com.flashnote.java.ui.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.flashnote.java.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GestureLockPatternView extends View {

    public enum DisplayMode {
        NORMAL,
        ERROR,
        CORRECT
    }

    public interface OnPatternListener {
        void onPatternStart();

        void onPatternProgress(@NonNull String pattern);

        void onPatternComplete(@NonNull String pattern);

        void onPatternCleared();
    }

    private static final int GRID_SIZE = 3;
    private static final int NODE_COUNT = GRID_SIZE * GRID_SIZE;

    private final float[] nodeCenterX = new float[NODE_COUNT];
    private final float[] nodeCenterY = new float[NODE_COUNT];
    private final boolean[] selectedNodes = new boolean[NODE_COUNT];
    private final List<Integer> selectedNodeOrder = new ArrayList<>();

    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodeInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int nodeColor;
    private int selectedNodeColor;
    private int errorColor;
    private int correctColor;

    private float nodeRadius;
    private float selectedNodeRadius;
    private float lineWidth;

    private boolean inProgress = false;
    private float currentTouchX;
    private float currentTouchY;
    private DisplayMode displayMode = DisplayMode.NORMAL;
    private OnPatternListener patternListener;

    public GestureLockPatternView(Context context) {
        super(context);
        init(context, null);
    }

    public GestureLockPatternView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public GestureLockPatternView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        nodeColor = ContextCompat.getColor(context, R.color.border);
        selectedNodeColor = ContextCompat.getColor(context, R.color.primary);
        errorColor = ContextCompat.getColor(context, R.color.danger);
        correctColor = ContextCompat.getColor(context, R.color.primary_dark);

        nodeRadius = dp(10f);
        selectedNodeRadius = dp(18f);
        lineWidth = dp(3f);

        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.GestureLockPatternView);
            nodeColor = ta.getColor(R.styleable.GestureLockPatternView_glpNodeColor, nodeColor);
            selectedNodeColor = ta.getColor(R.styleable.GestureLockPatternView_glpSelectedNodeColor, selectedNodeColor);
            errorColor = ta.getColor(R.styleable.GestureLockPatternView_glpErrorColor, errorColor);
            correctColor = ta.getColor(R.styleable.GestureLockPatternView_glpCorrectColor, correctColor);
            nodeRadius = ta.getDimension(R.styleable.GestureLockPatternView_glpNodeRadius, nodeRadius);
            selectedNodeRadius = ta.getDimension(R.styleable.GestureLockPatternView_glpSelectedNodeRadius, selectedNodeRadius);
            lineWidth = ta.getDimension(R.styleable.GestureLockPatternView_glpLineWidth, lineWidth);
            ta.recycle();
        }

        nodePaint.setStyle(Paint.Style.STROKE);
        nodePaint.setStrokeWidth(dp(1.5f));

        nodeInnerPaint.setStyle(Paint.Style.FILL);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeWidth(lineWidth);
    }

    public void setOnPatternListener(@Nullable OnPatternListener listener) {
        this.patternListener = listener;
    }

    public void setDisplayMode(@NonNull DisplayMode mode) {
        this.displayMode = mode;
        invalidate();
    }

    @NonNull
    public String getCurrentPattern() {
        StringBuilder builder = new StringBuilder(selectedNodeOrder.size());
        for (Integer node : selectedNodeOrder) {
            builder.append(node);
        }
        return builder.toString();
    }

    @NonNull
    public List<Integer> getCurrentPatternNodes() {
        return Collections.unmodifiableList(selectedNodeOrder);
    }

    public void clearPattern() {
        for (int i = 0; i < selectedNodes.length; i++) {
            selectedNodes[i] = false;
        }
        selectedNodeOrder.clear();
        inProgress = false;
        displayMode = DisplayMode.NORMAL;
        invalidate();
        if (patternListener != null) {
            patternListener.onPatternCleared();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float size = Math.min(w, h);
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;

        float cell = size / GRID_SIZE;
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int index = row * GRID_SIZE + col;
                nodeCenterX[index] = left + col * cell + cell / 2f;
                nodeCenterY[index] = top + row * cell + cell / 2f;
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int activeColor = resolveActiveColor();
        linePaint.setColor(activeColor);
        nodePaint.setColor(activeColor);
        nodeInnerPaint.setColor(activeColor);

        drawLines(canvas);
        drawNodes(canvas);
    }

    private int resolveActiveColor() {
        if (displayMode == DisplayMode.ERROR) {
            return errorColor;
        }
        if (displayMode == DisplayMode.CORRECT) {
            return correctColor;
        }
        return selectedNodeColor;
    }

    private void drawLines(@NonNull Canvas canvas) {
        if (selectedNodeOrder.isEmpty()) {
            return;
        }

        Path path = new Path();
        int firstNode = selectedNodeOrder.get(0);
        path.moveTo(nodeCenterX[firstNode], nodeCenterY[firstNode]);

        for (int i = 1; i < selectedNodeOrder.size(); i++) {
            int node = selectedNodeOrder.get(i);
            path.lineTo(nodeCenterX[node], nodeCenterY[node]);
        }

        if (inProgress) {
            path.lineTo(currentTouchX, currentTouchY);
        }

        canvas.drawPath(path, linePaint);
    }

    private void drawNodes(@NonNull Canvas canvas) {
        for (int i = 0; i < NODE_COUNT; i++) {
            float cx = nodeCenterX[i];
            float cy = nodeCenterY[i];

            if (selectedNodes[i]) {
                canvas.drawCircle(cx, cy, selectedNodeRadius, nodePaint);
                canvas.drawCircle(cx, cy, nodeRadius, nodeInnerPaint);
            } else {
                nodePaint.setColor(nodeColor);
                canvas.drawCircle(cx, cy, selectedNodeRadius, nodePaint);
                nodePaint.setColor(resolveActiveColor());
            }
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                clearPatternInternal(false);
                inProgress = true;
                currentTouchX = event.getX();
                currentTouchY = event.getY();
                addNodeIfHit(currentTouchX, currentTouchY, true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                currentTouchX = event.getX();
                currentTouchY = event.getY();
                addNodeIfHit(currentTouchX, currentTouchY, false);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                currentTouchX = event.getX();
                currentTouchY = event.getY();
                inProgress = false;
                notifyPatternComplete();
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void addNodeIfHit(float x, float y, boolean startEvent) {
        int hit = detectHitNode(x, y);
        if (hit < 0 || selectedNodes[hit]) {
            return;
        }
        selectedNodes[hit] = true;
        selectedNodeOrder.add(hit);
        if (patternListener != null) {
            if (startEvent) {
                patternListener.onPatternStart();
            }
            patternListener.onPatternProgress(getCurrentPattern());
        }
    }

    private int detectHitNode(float x, float y) {
        float hitRadius = selectedNodeRadius * 1.2f;
        for (int i = 0; i < NODE_COUNT; i++) {
            RectF hitRect = new RectF(
                    nodeCenterX[i] - hitRadius,
                    nodeCenterY[i] - hitRadius,
                    nodeCenterX[i] + hitRadius,
                    nodeCenterY[i] + hitRadius
            );
            if (hitRect.contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private void notifyPatternComplete() {
        if (patternListener != null && !selectedNodeOrder.isEmpty()) {
            patternListener.onPatternComplete(getCurrentPattern());
        }
    }

    private void clearPatternInternal(boolean notifyListener) {
        for (int i = 0; i < selectedNodes.length; i++) {
            selectedNodes[i] = false;
        }
        selectedNodeOrder.clear();
        displayMode = DisplayMode.NORMAL;
        if (notifyListener && patternListener != null) {
            patternListener.onPatternCleared();
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
