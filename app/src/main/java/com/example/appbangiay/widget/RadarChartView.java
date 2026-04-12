package com.example.appbangiay.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated Radar / Spider Chart
 */
public class RadarChartView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<String> labels = new ArrayList<>();
    private final List<Float> values = new ArrayList<>(); // 0..1
    private final List<Float> rawValues = new ArrayList<>();
    private float animProgress = 0f;

    public RadarChartView(Context context) { super(context); init(); }
    public RadarChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        gridPaint.setColor(Color.parseColor("#E5E7EB"));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);

        fillPaint.setColor(Color.parseColor("#3B82F6"));
        fillPaint.setAlpha(60);
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint.setColor(Color.parseColor("#3B82F6"));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3f);

        dotPaint.setColor(Color.parseColor("#1E40AF"));
        dotPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(Color.parseColor("#374151"));
        labelPaint.setTextSize(28f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint.setColor(Color.parseColor("#3B82F6"));
        valuePaint.setTextSize(24f);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        valueBgPaint.setColor(Color.parseColor("#F0F7FF"));
        valueBgPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<String> labels, List<Float> rawValues) {
        this.labels.clear();
        this.labels.addAll(labels);
        this.values.clear();
        this.rawValues.clear();
        this.rawValues.addAll(rawValues);

        // Normalize
        float max = 0;
        for (float v : rawValues) if (v > max) max = v;
        if (max == 0) max = 1;
        for (float v : rawValues) this.values.add(v / max);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1000);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
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
        if (n < 3) return;

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) * 0.65f;

        // Grid rings (3 levels)
        for (int ring = 1; ring <= 3; ring++) {
            float r = radius * ring / 3f;
            Path ringPath = new Path();
            for (int i = 0; i <= n; i++) {
                double angle = Math.PI * 2 * i / n - Math.PI / 2;
                float x = cx + (float) (r * Math.cos(angle));
                float y = cy + (float) (r * Math.sin(angle));
                if (i == 0) ringPath.moveTo(x, y);
                else ringPath.lineTo(x, y);
            }
            ringPath.close();
            canvas.drawPath(ringPath, gridPaint);
        }

        // Axis lines
        for (int i = 0; i < n; i++) {
            double angle = Math.PI * 2 * i / n - Math.PI / 2;
            float x = cx + (float) (radius * Math.cos(angle));
            float y = cy + (float) (radius * Math.sin(angle));
            canvas.drawLine(cx, cy, x, y, gridPaint);
        }

        // Data polygon
        Path dataPath = new Path();
        for (int i = 0; i < n; i++) {
            double angle = Math.PI * 2 * i / n - Math.PI / 2;
            float r = radius * values.get(i) * animProgress;
            float x = cx + (float) (r * Math.cos(angle));
            float y = cy + (float) (r * Math.sin(angle));
            if (i == 0) dataPath.moveTo(x, y);
            else dataPath.lineTo(x, y);
        }
        dataPath.close();
        canvas.drawPath(dataPath, fillPaint);
        canvas.drawPath(dataPath, strokePaint);

        // Dots + Labels + Values
        float labelSize = Math.max(22f, getHeight() * 0.05f);
        labelPaint.setTextSize(labelSize);
        valuePaint.setTextSize(labelSize * 0.85f);
        for (int i = 0; i < n; i++) {
            double angle = Math.PI * 2 * i / n - Math.PI / 2;
            float r = radius * values.get(i) * animProgress;
            float dx = cx + (float) (r * Math.cos(angle));
            float dy = cy + (float) (r * Math.sin(angle));
            canvas.drawCircle(dx, dy, 6f, dotPaint);

            // Label
            float lx = cx + (float) ((radius + 30) * Math.cos(angle));
            float ly = cy + (float) ((radius + 30) * Math.sin(angle)) + 8f;
            canvas.drawText(labels.get(i), lx, ly, labelPaint);

            // Value number near the data point
            if (i < rawValues.size() && animProgress > 0.5f) {
                float raw = rawValues.get(i);
                String valText;
                if (raw >= 1000) {
                    valText = String.format("%,.0f", raw);
                } else if (raw == (int) raw) {
                    valText = String.valueOf((int) raw);
                } else {
                    valText = String.format("%.1f", raw);
                }

                // Position value label slightly outward from data point
                float vx = cx + (float) ((r + 20) * Math.cos(angle));
                float vy = cy + (float) ((r + 20) * Math.sin(angle)) + 6f;

                // Draw background pill
                float tw = valuePaint.measureText(valText);
                android.graphics.RectF bg = new android.graphics.RectF(
                        vx - tw / 2 - 6, vy - labelSize * 0.85f, vx + tw / 2 + 6, vy + 4);
                canvas.drawRoundRect(bg, 6f, 6f, valueBgPaint);

                canvas.drawText(valText, vx, vy, valuePaint);
            }
        }
    }
}
