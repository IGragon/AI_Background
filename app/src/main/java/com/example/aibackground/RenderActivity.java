package com.example.aibackground;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class RenderActivity extends AppCompatActivity {

    private Uri originalImageUri;
    private Bitmap backgroundImage;
    private Bitmap finalImage;
    private ImageView imageView;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_render);

        originalImageUri = Uri.parse(getIntent().getStringExtra("image"));
        imageView = (ImageView) findViewById(R.id.imageView);

        Glide
                .with(this)
                .load(originalImageUri)
                .into(imageView);

        Log.d("originalImageUri", originalImageUri.toString());
        Log.d("imageView", String.valueOf(imageView != null));
    }

    public void backButton(View view){
        finish();
    }
}
