package com.example.appbangiay.customer.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Simple FlowLayout that wraps children to next line when exceeding width.
 */
public class FlowLayout extends ViewGroup {

    private int horizontalSpacing = 12;
    private int verticalSpacing = 12;

    public FlowLayout(Context context) { super(context); init(); }
    public FlowLayout(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public FlowLayout(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        horizontalSpacing = (int) (8 * density);
        verticalSpacing = (int) (8 * density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();

            if (x + cw > maxWidth) {
                x = 0;
                y += rowHeight + verticalSpacing;
                rowHeight = 0;
            }
            x += cw + horizontalSpacing;
            rowHeight = Math.max(rowHeight, ch);
        }
        y += rowHeight;
        setMeasuredDimension(
                resolveSize(maxWidth + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(y + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxWidth = r - l - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft(), y = getPaddingTop(), rowHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();

            if (x - getPaddingLeft() + cw > maxWidth) {
                x = getPaddingLeft();
                y += rowHeight + verticalSpacing;
                rowHeight = 0;
            }
            child.layout(x, y, x + cw, y + ch);
            x += cw + horizontalSpacing;
            rowHeight = Math.max(rowHeight, ch);
        }
    }
}
