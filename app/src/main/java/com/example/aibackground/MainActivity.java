package com.example.aibackground;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static final int ACTIVITY_START_CAMERA_APP = 42; // просто два кода запроса, чтобы отличать один от другого
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_AND_CAMERA = 69;
    private static final int ACTIVITY_GET_IMAGE_FROM_GALLERY = 404;
    private Bitmap currentImage;
    private ImageView imageShowImageView;
    private String imageFileLocation;
    float guideline_percent = .9f;
    float ratio;
    int displayHeight;
    int displayWidth;

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

    public File makePhotoLocation() throws IOException { // создание файла для фото
        String dateTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imgFileName = "IMAGE_" + dateTime + "_";

        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        Log.d("makePhotoLocation", imgFileName);
        File image = File.createTempFile(imgFileName, ".JPG", storageDirectory); // тут всё крашится, почему - неизвестно

        imageFileLocation = image.getAbsolutePath();
        return image;

    }

    public void takePicture(View view) { // сделать фотку
        requestRuntimePermission(); // хотя запрос идёт ещё перед всем действием, но приложение крашится так и не получив его
        Intent callCamera = new Intent();

        callCamera.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            photoFile = makePhotoLocation();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("takePicture", "catch");
        }
        //callCamera.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile)); // photoFile == null в этом проблема, нужно решить

        startActivityForResult(callCamera, ACTIVITY_START_CAMERA_APP);
    }

    public void takePictureFromGallery(View view){
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        startActivityForResult(Intent.createChooser(intent, getString(R.string.get_picture)), ACTIVITY_GET_IMAGE_FROM_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_START_CAMERA_APP) { // если получили картинку, что создается Bitmap и вставляется в ImageView на основном экране
            Bitmap photoCapturedBitmap = BitmapFactory.decodeFile(imageFileLocation);
            imageShowImageView.setImageBitmap(photoCapturedBitmap);
        }else if (requestCode == ACTIVITY_GET_IMAGE_FROM_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) { // если получили картинку, что создается Bitmap и вставляется в ImageView на основном экране
            Uri uri = data.getData();
            try {
                currentImage = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                currentImage = currentImage.copy(currentImage.getConfig(), true);
                System.out.println(displayHeight + " " + displayWidth);

                ratio = ((float) currentImage.getHeight()) / ((float)currentImage.getWidth()); // изменение размера картинки, чтобы нормально помещалось в imageView
                if (ratio > 1){
                    imageShowImageView.setImageBitmap(Bitmap.createScaledBitmap(currentImage, (int)((displayHeight * guideline_percent) / ratio), (int)(displayHeight * guideline_percent), false));
                }else{
                    imageShowImageView.setImageBitmap(Bitmap.createScaledBitmap(currentImage, displayWidth, (int)(displayWidth / ratio), false));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
