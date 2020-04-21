package com.example.aibackground;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static final int ACTIVITY_START_CAMERA_APP = 42; // просто коды запроса, чтобы отличать один от другого
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_AND_CAMERA = 69;
    private static final int REQUEST_CAMERA = 123;
    private static final int ACTIVITY_GET_IMAGE_FROM_GALLERY = 404;
    private Bitmap currentImage;
    private ImageView imageShowImageView;
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
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void dispatchTakePictureIntent(View view) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // Ensure that there's a camera activity to handle the intent
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                // Create the File where the photo should go
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    // Error occurred while creating the File
                    Toast.makeText(this, "photoFile is null!", Toast.LENGTH_SHORT).show();
                }
                // Continue only if the File was successfully created
                if (photoFile != null) {
                    photoURI = FileProvider.getUriForFile(this,
                            "com.example.android.provider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, ACTIVITY_START_CAMERA_APP);
                }
            }
        }
    }

    public void takePictureFromGallery(View view) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        startActivityForResult(Intent.createChooser(intent, getString(R.string.get_picture)), ACTIVITY_GET_IMAGE_FROM_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_START_CAMERA_APP && resultCode == RESULT_OK) { // если получили картинку, то создается Bitmap и вставляется в ImageView на основном экране
            Bitmap photoFromCamera = null;
            try {
                photoFromCamera = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoURI);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println(photoURI.toString());
            if (photoFromCamera != null) {
                putImageInImageView(photoFromCamera, photoURI);
            } else {
                Toast.makeText(this, "photoFromCamera is null!", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == ACTIVITY_GET_IMAGE_FROM_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) { // если получили картинку из галереи, то создается Bitmap и вставляется в ImageView на основном экране
            Uri uri = data.getData();
            try {
                Bitmap photoFromGallery = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                putImageInImageView(photoFromGallery, uri);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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

    private Bitmap makeBitmapToFitImageView(Bitmap bitmap) { // изменение размера картинки, чтобы нормально помещалось в imageView
        ratio = ((float) bitmap.getHeight()) / ((float) bitmap.getWidth());
        if (ratio > 1) {
            bitmap = Bitmap.createScaledBitmap(bitmap, (int) ((displayHeight * guideline_percent) / ratio), (int) (displayHeight * guideline_percent), false);
        } else {
            bitmap = Bitmap.createScaledBitmap(bitmap, displayWidth, (int) (displayWidth * ratio), false);
        }
        return bitmap;
    }

    private void putImageInImageView(Bitmap bitmap, Uri uri) {
        ExifInterface exif;
        try{
            exif = new ExifInterface(uri.toString());
            currentImage = bitmap.copy(bitmap.getConfig(), true);
            currentImage = rotateBitmap(currentImage, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1));
            currentImage = makeBitmapToFitImageView(currentImage); // если прочитать название функции, то в принципе понятно, что она делает

            imageShowImageView.setImageBitmap(currentImage);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotation){
        Matrix matrix = new Matrix();
        switch (rotation){
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                matrix.postRotate(0);
                break;
        }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return bitmap;
    }
}
