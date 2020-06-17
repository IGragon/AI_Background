package com.example.aibackground;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import androidx.core.app.ShareCompat;

public class PreviewActivity extends AppCompatActivity {
    private int imageOrientation; // ориентация изображения
    private Bitmap currentImageBitmap;
    private ImageView imageViewPreview; // UI
    private Uri currentImageUri; // Uri для отправки в следующую активити

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) { // во время создания активити
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        imageViewPreview = (ImageView) findViewById(R.id.imageViewPreview); // Ui

        ShareCompat.IntentReader intentReader = ShareCompat.IntentReader.from(this); // если "поделились" в это приложение
        if (intentReader.isShareIntent()) { // из интента получаем Uri
            currentImageUri = intentReader.getStream();
            fromUriToImageView();
        }else{
            currentImageUri = Uri.parse(getIntent().getStringExtra("uri"));
            fromUriToImageView();
        }
    }

    public void back(View view){
        finish();
    }

    public void rotateLeft(View view) { // поворачиваем изображение влево
        imageOrientation -= 90;
        imageViewPreview.setRotation(imageOrientation);
    }

    public void rotateRight(View view) { // поворачиваем изображение вправо
        imageOrientation += 90;
        imageViewPreview.setRotation(imageOrientation);
    }

    public void renderImage(View view) { // отправляемся в другую активити, где будет происходить обработка изображения
        if (currentImageUri != null) {
            Intent intent = new Intent(this, RenderActivity.class);
            intent.putExtra("image", currentImageUri.toString());
            intent.putExtra("orientation", imageOrientation);
            Log.d("Prerender state", currentImageUri.toString() + '\n' + imageOrientation);
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.error_trying_to_render_empty_image, Toast.LENGTH_SHORT).show();
        }
    }

    private void fromUriToImageView() {
        try {
            imageOrientation = 0;
            currentImageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), currentImageUri);
            imageViewPreview.setRotation(imageOrientation);
            imageViewPreview.setImageBitmap(currentImageBitmap);
        } catch (Exception e) {
            currentImageBitmap = null;
            currentImageUri = null;
            e.printStackTrace();
            Toast.makeText(this, R.string.error_file_loading_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
