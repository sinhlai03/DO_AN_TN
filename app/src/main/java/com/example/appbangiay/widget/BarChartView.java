package com.example.appbangiay.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated Horizontal Bar Chart
 */
public class BarChartView extends View {

    private static final int[] BAR_COLORS = {
            0xFF3B82F6, // blue
            0xFFF59E0B, // amber
            0xFF10B981, // green
            0xFFEC4899, // pink
            0xFF8B5CF6  // purple
    };

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<String> labels = new ArrayList<>();
    private final List<Float> values = new ArrayList<>();
    private final List<String> displayValues = new ArrayList<>();
    private float animProgress = 0f;

    public BarChartView(Context context) { super(context); init(); }
    public BarChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        bgBarPaint.setColor(Color.parseColor("#F3F4F6"));
        bgBarPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(Color.parseColor("#374151"));
        labelPaint.setTextSize(28f);
        labelPaint.setTextAlign(Paint.Align.LEFT);

        valuePaint.setTextSize(26f);
        valuePaint.setTextAlign(Paint.Align.LEFT);
        valuePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    public void setData(List<String> labels, List<Float> rawValues, List<String> displayValues) {
        this.labels.clear();
        this.labels.addAll(labels);
        this.values.clear();
        this.displayValues.clear();
        this.displayValues.addAll(displayValues);

        float max = 0;
        for (float v : rawValues) if (v > max) max = v;
        if (max == 0) max = 1;
        for (float v : rawValues) this.values.add(v / max);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(900);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            animProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = labels.size();
        if (n == 0) return;

        float w = getWidth();
        float h = getHeight();
        float labelSize = Math.max(24f, h * 0.055f);
        labelPaint.setTextSize(labelSize);
        valuePaint.setTextSize(labelSize * 0.9f);

        float labelWidth = 0;
        for (String lbl : labels) {
            float tw = labelPaint.measureText(lbl);
            if (tw > labelWidth) labelWidth = tw;
        }
        labelWidth += 16f;

        float rowH = h / n;
        float barH = rowH * 0.38f;
        float cornerR = barH / 2f;
        float barAreaW = w - labelWidth - 80f; // 80 for value text on right

        for (int i = 0; i < n; i++) {
            float cy = rowH * i + rowH / 2f;
            float barTop = cy - barH / 2f;
            float barBot = cy + barH / 2f;

            // Background bar
            RectF bgRect = new RectF(labelWidth, barTop, labelWidth + barAreaW, barBot);
            canvas.drawRoundRect(bgRect, cornerR, cornerR, bgBarPaint);

            // Colored bar
            float barW = barAreaW * values.get(i) * animProgress;
            if (barW > cornerR * 2) {
                barPaint.setColor(BAR_COLORS[i % BAR_COLORS.length]);
                RectF barRect = new RectF(labelWidth, barTop, labelWidth + barW, barBot);
                canvas.drawRoundRect(barRect, cornerR, cornerR, barPaint);
            }

            // Label (left)
            float textY = cy + labelSize * 0.35f;
            canvas.drawText(labels.get(i), 0, textY, labelPaint);

            // Value (right of bar)
            if (animProgress > 0.3f && i < displayValues.size()) {
                valuePaint.setColor(BAR_COLORS[i % BAR_COLORS.length]);
                float vx = labelWidth + barAreaW + 8f;
                canvas.drawText(displayValues.get(i), vx, textY, valuePaint);
            }
        }
    }
}
