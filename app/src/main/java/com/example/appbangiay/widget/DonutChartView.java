package com.example.appbangiay.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated Donut Chart with labels
 */
public class DonutChartView extends View {

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final List<Slice> slices = new ArrayList<>();
    private float animProgress = 0f;
    private String centerText = "";

    private static final int[] COLORS = {
            Color.parseColor("#3B82F6"), // blue
            Color.parseColor("#EF4444"), // red
            Color.parseColor("#10B981"), // green
            Color.parseColor("#F59E0B"), // amber
            Color.parseColor("#8B5CF6"), // purple
            Color.parseColor("#EC4899"), // pink
            Color.parseColor("#06B6D4"), // cyan
    };

    public DonutChartView(Context context) { super(context); init(); }
    public DonutChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setColor(Color.parseColor("#374151"));
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        centerPaint.setColor(Color.parseColor("#1E40AF"));
        centerPaint.setTextSize(36f);
        centerPaint.setTextAlign(Paint.Align.CENTER);
        centerPaint.setFakeBoldText(true);
    }

    public void setData(List<String> labels, List<Float> values) {
        slices.clear();
        float total = 0;
        for (float v : values) total += v;
        if (total == 0) {
            centerText = "0 đ";
            animProgress = 1f;
            invalidate();
            return;
        }

        for (int i = 0; i < labels.size(); i++) {
            float pct = values.get(i) / total * 100f;
            slices.add(new Slice(labels.get(i), values.get(i), pct, COLORS[i % COLORS.length]));
        }
        centerText = String.format("%,.0f đ", total);

        // Animate
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1200);
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

        float w = getWidth();
        float h = getHeight();
        float chartSize = Math.min(w * 0.55f, h * 0.8f);
        float strokeWidth = chartSize * 0.18f;
        arcPaint.setStrokeWidth(strokeWidth);

        float cx = w * 0.35f;
        float cy = h * 0.5f;
        float r = (chartSize - strokeWidth) / 2f;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        if (slices.isEmpty()) {
            // Draw empty gray ring
            arcPaint.setColor(Color.parseColor("#E5E7EB"));
            canvas.drawArc(oval, 0, 360, false, arcPaint);
            // Center text "0 đ"
            centerPaint.setTextSize(r * 0.3f);
            canvas.drawText(centerText, cx, cy + centerPaint.getTextSize() * 0.35f, centerPaint);
            // "Không có dữ liệu" label
            textPaint.setTextSize(Math.max(24f, h * 0.06f));
            textPaint.setTextAlign(Paint.Align.LEFT);
            float legendX = w * 0.65f;
            float legendY = cy;
            textPaint.setColor(Color.parseColor("#9CA3AF"));
            canvas.drawText("Không có dữ liệu", legendX, legendY, textPaint);
            textPaint.setColor(Color.parseColor("#374151"));
            return;
        }


        // Draw arcs
        float startAngle = -90f;
        for (Slice s : slices) {
            float sweep = s.percent / 100f * 360f * animProgress;
            arcPaint.setColor(s.color);
            canvas.drawArc(oval, startAngle, sweep, false, arcPaint);
            startAngle += sweep;
        }

        // Center text
        centerPaint.setTextSize(r * 0.3f);
        canvas.drawText(centerText, cx, cy + centerPaint.getTextSize() * 0.35f, centerPaint);

        // Legend (right side)
        float legendX = w * 0.65f;
        float legendY = cy - (slices.size() * 40f) / 2f;
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(Math.max(24f, h * 0.06f));
        textPaint.setTextAlign(Paint.Align.LEFT);

        for (Slice s : slices) {
            dotPaint.setColor(s.color);
            canvas.drawCircle(legendX, legendY - 4f, 8f, dotPaint);
            canvas.drawText(s.label + " " + String.format("%.0f%%", s.percent * animProgress),
                    legendX + 18f, legendY + 4f, textPaint);
            legendY += 44f;
        }
    }

    static class Slice {
        String label;
        float value, percent;
        int color;
        Slice(String label, float value, float percent, int color) {
            this.label = label;
            this.value = value;
            this.percent = percent;
            this.color = color;
        }
    }
}
