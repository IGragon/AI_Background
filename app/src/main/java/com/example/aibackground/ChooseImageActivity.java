package com.example.aibackground;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChooseImageActivity extends AppCompatActivity {
    private static final int ACTIVITY_START_CAMERA_APP = 42; // просто коды запроса, чтобы отличать один от другого
    private static final int REQUEST_PERMISSIONS = 69;
    private static final int REQUEST_CAMERA = 123;
    private static final int ACTIVITY_GET_IMAGE_FROM_GALLERY = 404;
    private int imageOrientation; // ориентация изображения
    private Bitmap currentImageBitmap;
    private ImageView imageShowImageView; // UI
    private ImageButton btnRotateLeft, btnRotateRight;
    private File photoFile; // имя для фото
    private Uri photoURI; // Uri фото
    private Uri currentImageUri; // Uri для отправки в следующую активити

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) { // во время создания активити
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageShowImageView = (ImageView) findViewById(R.id.imageViewPreview); // Ui
        btnRotateLeft = (ImageButton) findViewById(R.id.buttonRotateLeft);
        btnRotateRight = (ImageButton) findViewById(R.id.buttonRotateRight);
        requestRuntimePermissions(); // запрашивем разрешения

        ShareCompat.IntentReader intentReader = ShareCompat.IntentReader.from(this); // если "поделились" в это приложение
        if (intentReader.isShareIntent()) { // из интента получаем Uri
            currentImageUri = intentReader.getStream();
            btnRotateLeft.setVisibility(View.VISIBLE);
            btnRotateRight.setVisibility(View.VISIBLE);
            fromUriToImageView();
        }
    }

    public void requestRuntimePermissions() { // запрос разрешения на доступ к памяти и камере
        if (Build.VERSION.SDK_INT >= 23) { // если сдк не меньше 23, то запрашиваем программно, иначе прописаных в манифесте достаточно
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, REQUEST_PERMISSIONS);
            }
        }
    }

    private File createImageFile() throws IOException { // Создание файла для фото
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* префикс */
                ".png",         /* суффикс */
                storageDir      /* директория */
        );

        return image;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void dispatchTakePhotoIntent(View view) { // сделать фото с помощью приложения камеры
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        } else {
            Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePhotoIntent.resolveActivity(getPackageManager()) != null) {
                photoFile = null;
                try { // создаем файл для фото
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Log.d("ChooseImageActivity", "dispatchTakePhotoIntent: photoFile is null");
                }
                if (photoFile != null) { // запускем камеру
                    photoURI = FileProvider.getUriForFile(this,
                            "com.example.aibackground.provider",
                            photoFile);
                    takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePhotoIntent, ACTIVITY_START_CAMERA_APP);
                }
            }
        }
    }

    public void chooseImageFromGallery(View view) { // получить изображение из галереи
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        startActivityForResult(Intent.createChooser(intent, getString(R.string.get_image)), ACTIVITY_GET_IMAGE_FROM_GALLERY);
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

    public void rotateLeft(View view) { // поворачиваем изображение влево
        imageOrientation -= 90;
        imageShowImageView.setRotation(imageOrientation);
    }

    public void rotateRight(View view) { // поворачиваем изображение вправо
        imageOrientation += 90;
        imageShowImageView.setRotation(imageOrientation);
    }

    private void fromUriToImageView() {
        try {
            imageOrientation = 0;
            currentImageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), currentImageUri);
            imageShowImageView.setRotation(imageOrientation);
            imageShowImageView.setImageBitmap(currentImageBitmap);
            btnRotateLeft.setVisibility(View.VISIBLE);
            btnRotateRight.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            currentImageBitmap = null;
            currentImageUri = null;
            e.printStackTrace();
            Toast.makeText(this, R.string.error_file_loading_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_START_CAMERA_APP && resultCode == RESULT_OK) { // если получили картинку из камеры
            imageShowImageView.setImageDrawable(null);

            currentImageUri = photoURI;
            fromUriToImageView();
        } else if (requestCode == ACTIVITY_GET_IMAGE_FROM_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) { // если получили картинку из галереи
            imageShowImageView.setImageDrawable(null);

            currentImageUri = data.getData();
            fromUriToImageView();
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { // обработка результатов запроса на разрешения
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.camera_permission_granted, Toast.LENGTH_LONG).show();
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, ACTIVITY_START_CAMERA_APP);
            } else {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED
                    && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.all_permissions_are_granted, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.not_all_permissions_are_granted, Toast.LENGTH_LONG).show();
            }
        }
    }
}
