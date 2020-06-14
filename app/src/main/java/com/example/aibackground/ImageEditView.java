package com.example.aibackground;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

public class ImageEditView extends View {
    private Bitmap cutImage, backgroundImage;
    private int canvasSize;


    public ImageEditView(Context context, AttributeSet attributeSet){
        super(context, attributeSet);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        if (backgroundImage != null){
            canvas.drawBitmap(backgroundImage, 0, 0, null);
        }
        if (cutImage != null){
            int left = getWidth() / 2 - cutImage.getWidth() / 2;
            canvas.drawBitmap(cutImage, left, 0, null);
        }
        canvas.restore();
    }

    public void setCutImage(Bitmap cutImage) {
        this.cutImage = cutImage;
    }

    public void setBackgroundImage(Bitmap backgroundImage) {
        this.backgroundImage = backgroundImage;
    }
}
