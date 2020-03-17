package com.example.aibackground;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.InputConfiguration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static final int ACTIVITY_START_CAMERA_APP = 42; // просто два кода запроса, чтобы отличать один от другого
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 69;
    private ImageView photoCaptureImageView;
    private String imageFileLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        photoCaptureImageView = findViewById(R.id.imageViewTakenPhoto);
    }

    public void requestRuntimePermission() { // запрос разрешения на доступ к памяти
        if (Build.VERSION.SDK_INT >= 23) { // если сдк не меньше 23, то запрашиваем программно, иначе прописагых в манифесте достаточно
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    public File makePhotoLocation() throws IOException { // создание файла для фото
        String dateTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imgFileName = "IMAGE_" + dateTime + "_";

        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
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
        callCamera.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile)); // photoFile == null в этом проблема, нужно решить

        startActivityForResult(callCamera, ACTIVITY_START_CAMERA_APP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_START_CAMERA_APP) { // если получили картинку, что создается Bitmap и вставляется в ImageView на основном экране
            Bitmap photoCapturedBitmap = BitmapFactory.decodeFile(imageFileLocation);
            photoCaptureImageView.setImageBitmap(photoCapturedBitmap);
        }
    }
}
