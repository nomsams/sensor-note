package org.havenapp.main.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import android.view.View;
import android.graphics.Paint;

import java.util.LinkedList;

/**
 * Created by n8fr8 on 10/30/17.
 */

public class SimpleWaveformExtended extends View {


    private int mThreshold = 0;
    private int lineY;
    private int maxVal = 100; // default max value of slider
    private final LinkedList<Integer> data = new LinkedList<>();
    private Paint barPencilFirst = new Paint();
    private int barGap = 2;

    public int width;

    public SimpleWaveformExtended(Context context) {
        super(context);
    }

    public SimpleWaveformExtended(Context context, AttributeSet attrs) {
        super(context, attrs);
        barPencilFirst.setStrokeWidth(3);
    }

    public void setMaxVal(int max_val) {
        this.maxVal = max_val;
    }

    public void init() {
        data.clear();
        invalidate();
    }

    public void setDataList(LinkedList<Integer> values) {
        data.clear();
        if (values != null) data.addAll(values);
    }

    public void refresh() {
        invalidate();
    }

    public void setThreshold (int threshold)
    {
        mThreshold  = threshold;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int midY = getHeight()/2;
        lineY =  midY - (int) (((float) mThreshold/ maxVal) * midY);
        canvas.drawLine(0,lineY,getWidth(),lineY,barPencilFirst);

        if (data.isEmpty()) return;
        float barWidth = Math.max(1.0f, (float)(getWidth() - getPaddingLeft() - getPaddingRight()) / data.size());
        float x = getPaddingLeft();
        for (Integer value : data) {
            int amplitude = (int)(((float)value / maxVal) * midY);
            canvas.drawLine(x, midY - amplitude, x, midY + amplitude, barPencilFirst);
            x += barWidth + barGap;
            if (x > getWidth()) break;
        }
        width = getWidth();
    }
}
