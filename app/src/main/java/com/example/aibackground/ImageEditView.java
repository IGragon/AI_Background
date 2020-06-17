package com.example.aibackground;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class ImageEditView extends View {
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private Bitmap originalCutImage, cutImage, backgroundImage;
    private float scale = 1;
    private int cutImgWidth, cutImgHeight;
    private int cutLeft, cutTop;

    public ImageEditView(Context context, AttributeSet attributeSet){
        super(context, attributeSet);
        scaleGestureDetector = new ScaleGestureDetector(context, new MyScaleGestureListener());
        gestureDetector = new GestureDetector(context, new MyGestureListener());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        if (backgroundImage != null){
            int left = getWidth() / 2 - backgroundImage.getWidth() / 2;
            int top = getHeight() / 2 - backgroundImage.getHeight() / 2;
            canvas.drawBitmap(backgroundImage, left, top, null);
        }
        if (cutImage != null){
            canvas.drawBitmap(cutImage, cutLeft, cutTop, null);
        }
        canvas.restore();
    }

    public void setCutImage(Bitmap cutImage) {

        if (cutImage != null){
            this.originalCutImage = cutImage;
            float ratio = (float) cutImage.getWidth() / cutImage.getHeight();
            if (ratio > 1){
                cutImgWidth = getWidth();
                cutImgHeight = (int) (getWidth() / ratio);
            }else{
                cutImgWidth = (int) (getHeight()  * ratio);
                cutImgHeight = getHeight();
            }
            cutImage = Bitmap.createScaledBitmap(cutImage, cutImgWidth, cutImgHeight, false);
            cutLeft = getWidth() / 2 - cutImgWidth / 2;
            cutTop = getHeight() / 2 - cutImgHeight / 2;
            Log.d("ImageEditView", "setCutImage: " + cutImgWidth + " " + cutImgHeight);
        }
        this.cutImage = cutImage;
        invalidate();
    }

    public void setBackgroundImage(Bitmap bgImage) {
        int bgWidth, bgHeight;
        if (bgImage != null){
            float ratio = (float) bgImage.getWidth() / bgImage.getHeight();
            if (ratio > 1){
                bgWidth = getWidth();
                bgHeight = (int) (getWidth() / ratio);
            }else{
                bgWidth = (int) (getHeight()  * ratio);
                bgHeight = getHeight();
            }
            bgImage = Bitmap.createScaledBitmap(bgImage, bgWidth, bgHeight, false);
            Log.d("ImageEditView", "setBackgroundImage: " + bgWidth + " " + bgHeight);
        }
        this.backgroundImage = bgImage;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private class MyScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            if (scale * scaleFactor > 0.1 && scale * scaleFactor < 5) {
                scale *= scaleFactor;
                cutLeft -= ((int) (cutImgWidth * scale) - cutImage.getWidth()) / 2;
                cutTop -= ((int) (cutImgHeight * scale) - cutImage.getHeight()) / 2;
                cutImage = Bitmap.createScaledBitmap(originalCutImage, (int) (cutImgWidth * scale), (int) (cutImgHeight * scale), false);
                invalidate();
            }
            return true;
        }
    }

    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener{
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            cutLeft -= distanceX;
            cutTop -= distanceY;
            invalidate();
            return true;
        }
    }
}
