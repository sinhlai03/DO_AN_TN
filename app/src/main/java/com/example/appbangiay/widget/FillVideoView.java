package com.example.appbangiay.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * A VideoView that always fills its container, regardless of the video's native dimensions.
 * Fixes the issue where VideoView shrinks to the video's natural size when playback starts.
 */
public class FillVideoView extends VideoView {

    public FillVideoView(Context context) { super(context); }
    public FillVideoView(Context context, AttributeSet attrs) { super(context, attrs); }
    public FillVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Always fill exactly the space given by the parent — no shrinking on playback
        int width  = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }
}
