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
    private Bitmap originalCutImage, cutImage, originalBackgroundImage, backgroundImage;
    private float scale = 1;
    private int cutImgWidth, cutImgHeight;
    private int cutLeft, cutTop;
    private int bgLeft, bgTop;

    public ImageEditView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        scaleGestureDetector = new ScaleGestureDetector(context, new MyScaleGestureListener());
        gestureDetector = new GestureDetector(context, new MyGestureListener());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        if (backgroundImage != null) {
            canvas.drawBitmap(backgroundImage, bgLeft, bgTop, null);
        }
        if (cutImage != null) {
            canvas.drawBitmap(cutImage, cutLeft, cutTop, null);
        }
        canvas.restore();
    }

    public void setCutImage(Bitmap cutImage) {
        if (cutImage != null) {
            this.originalCutImage = cutImage;
            float ratio = (float) cutImage.getWidth() / cutImage.getHeight();
            if (ratio > 1) {
                cutImgWidth = getWidth();
                cutImgHeight = (int) (getWidth() / ratio);
            } else {
                cutImgWidth = (int) (getHeight() * ratio);
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
        if (bgImage != null) {
            this.originalBackgroundImage = bgImage;
            float ratio = (float) bgImage.getWidth() / bgImage.getHeight();
            if (ratio > 1) {
                bgWidth = getWidth();
                bgHeight = (int) (getWidth() / ratio);
            } else {
                bgWidth = (int) (getHeight() * ratio);
                bgHeight = getHeight();
            }
            bgImage = Bitmap.createScaledBitmap(bgImage, bgWidth, bgHeight, false);
            bgLeft = getWidth() / 2 - bgWidth / 2;
            bgTop = getHeight() / 2 - bgHeight / 2;
            Log.d("ImageEditView", "setBackgroundImage: " + bgWidth + " " + bgHeight);
        }
        this.backgroundImage = bgImage;
        invalidate();
    }

    public Bitmap returnCombinedBitmap() {
        Bitmap resultBitmap, rescaledCutImage;
        int viewWidth = getWidth();
        int fromLeft, fromTop;
        int distLeft, distTop;
        int originalBgWidth = originalBackgroundImage.getWidth(), originalBgHeight = originalBackgroundImage.getHeight();
        float pixelRatio = (float) originalBgWidth / (float) viewWidth;

        resultBitmap = originalBackgroundImage.copy(originalBackgroundImage.getConfig(), true);
        rescaledCutImage = Bitmap.createScaledBitmap(originalCutImage, (int) ((float) cutImgWidth * pixelRatio * scale), (int) ((float) cutImgHeight * pixelRatio * scale), false);
        fromLeft = (int) ((float) cutLeft * pixelRatio);
        fromTop = (int) ((float) cutTop * pixelRatio);
        distLeft = (int) ((float) bgLeft * pixelRatio);
        distTop = (int) ((float) bgTop * pixelRatio);

        for (int i = 0; i < rescaledCutImage.getHeight(); ++i) {
            for (int j = 0; j < rescaledCutImage.getWidth(); ++j) {
                if (j + fromLeft - distLeft > 0 && j + fromLeft - distLeft < originalBgWidth &&
                        i + fromTop - distTop > 0 && i + fromTop - distTop < originalBgHeight) {
                    if (rescaledCutImage.getPixel(j, i) != 0) {
                        resultBitmap.setPixel(j + fromLeft - distLeft, i + fromTop - distTop, rescaledCutImage.getPixel(j, i));
                    }
                }
            }
        }
        return resultBitmap;
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
            if (scale * scaleFactor > 0.3 && scale * scaleFactor < 3) {
                RenderActivity.imageSaved = false;
                scale *= scaleFactor;
                cutLeft -= ((int) (cutImgWidth * scale) - cutImage.getWidth()) / 2;
                cutTop -= ((int) (cutImgHeight * scale) - cutImage.getHeight()) / 2;
                cutImage = Bitmap.createScaledBitmap(originalCutImage, (int) (cutImgWidth * scale), (int) (cutImgHeight * scale), false);
                invalidate();
            }
            return true;
        }
    }

    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            RenderActivity.imageSaved = false;
            cutLeft -= distanceX;
            cutTop -= distanceY;
            invalidate();
            return true;
        }
    }
}
