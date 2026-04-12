package com.example.appbangiay.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * VideoView that keeps its measured size fixed (no auto-resize when video plays).
 */
public class FixedVideoView extends VideoView {

    public FixedVideoView(Context context) { super(context); }
    public FixedVideoView(Context context, AttributeSet attrs) { super(context, attrs); }
    public FixedVideoView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Always use exactly the size given by parent — ignore video's native dimensions
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
    }
}
