package com.example.aibackground;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static final int ACTIVITY_START_CAMERA_APP = 42; // просто коды запроса, чтобы отличать один от другого
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_AND_CAMERA = 69;
    private static final int REQUEST_CAMERA = 123;
    private static final int ACTIVITY_GET_IMAGE_FROM_GALLERY = 404;
    private Uri currentImageUri;
    private ImageView imageShowImageView;
    private Button renderButton;
    private String mCurrentPhotoPath;
    private File storageDir;
    private Uri photoURI;
    float guideline_percent = .9f;
    float ratio;
    int displayHeight;
    int displayWidth;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        displayWidth = size.x;
        displayHeight = size.y;

        imageShowImageView = findViewById(R.id.imageShow);
        renderButton = findViewById(R.id.buttonRender);
        requestRuntimePermission();
    }

    public void requestRuntimePermission() { // запрос разрешения на доступ к памяти
        if (Build.VERSION.SDK_INT >= 23) { // если сдк не меньше 23, то запрашиваем программно, иначе прописаных в манифесте достаточно
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, REQUEST_WRITE_EXTERNAL_STORAGE_AND_CAMERA);
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* префикс */
                ".jpg",         /* суффикс */
                storageDir      /* директория */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void dispatchTakePhotoIntent(View view) { // сделать фото с помощью приложения камеры
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePhotoIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Toast.makeText(this, "photoFile is null!", Toast.LENGTH_SHORT).show();
                }
                if (photoFile != null) {
                    photoURI = FileProvider.getUriForFile(this,
                            "com.example.android.provider",
                            photoFile);
                    takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePhotoIntent, ACTIVITY_START_CAMERA_APP);
                }
            }
        }
    }

    public void takeImageFromGallery(View view) { // получить изображение из галереи
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        startActivityForResult(Intent.createChooser(intent, getString(R.string.get_image)), ACTIVITY_GET_IMAGE_FROM_GALLERY);
    }

    public void renderImage(View view) { // отправляемся в другую активити, где будет происходить обработка изображения
        Intent intent = new Intent(this, RenderActivity.class);
        intent.putExtra("image", currentImageUri.toString());

        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_START_CAMERA_APP && resultCode == RESULT_OK) { // если получили картинку, то вставляем ее в ImageView на основном экране
            imageShowImageView.setImageDrawable(null);
            Glide
                    .with(this)
                    .load(photoURI)
                    .into(imageShowImageView);
            currentImageUri = photoURI;
            Log.d("picture is in here: ", photoURI.toString());
        } else if (requestCode == ACTIVITY_GET_IMAGE_FROM_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) { // если получили картинку из галереи, то вставляем ее в ImageView на основном экране
            imageShowImageView.setImageDrawable(null);
            Uri uri = data.getData();
            Glide
                    .with(this)
                    .load(uri)
                    .into(imageShowImageView);
            currentImageUri = uri;
        }

        if (currentImageUri != null) {
            renderButton.setEnabled(true);
        } else {
            renderButton.setEnabled(false);
        }

    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { // обработка результатов запроса на разрешения
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, ACTIVITY_START_CAMERA_APP);
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }
}
