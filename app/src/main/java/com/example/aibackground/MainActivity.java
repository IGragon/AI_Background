package com.example.aibackground;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.aibackground.preferences.PreferenceActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.example.aibackground.utils.LocaleHelper;

public class MainActivity extends AppCompatActivity { // приветственная активити
    private static final int ACTIVITY_START_CAMERA_APP = 42; // просто коды запроса, чтобы отличать один от другого
    private static final int REQUEST_PERMISSIONS = 69;
    private static final int REQUEST_CAMERA = 123;
    private static final int ACTIVITY_GET_IMAGE_FROM_GALLERY = 404;

    private Uri photoURI; // Uri фото
    private Uri currentImageUri; // Uri для передачи в следующую активити
    private File photoFile; // имя для фото

    //PREFERENCES
    private SharedPreferences sp;
    private String mLanguageCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        mLanguageCode = sp.getString("lang", "0");
        init();
        LocaleHelper.setLocale(MainActivity.this, mLanguageCode);
        setContentView(R.layout.activity_main);

        requestRuntimePermissions(); // запрашивем разрешения


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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_START_CAMERA_APP && resultCode == RESULT_OK) { // если получили картинку из камеры
            currentImageUri = photoURI;
        } else if (requestCode == ACTIVITY_GET_IMAGE_FROM_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) { // если получили картинку из галереи
            currentImageUri = data.getData();
        }

        if (currentImageUri != null){
            Intent intentToPreviewActivity = new Intent(this, PreviewActivity.class);
            intentToPreviewActivity.putExtra("uri", currentImageUri.toString());
            Log.d("Main Activity", "Starting PreviewActivity");
            startActivity(intentToPreviewActivity);
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

    public void openPrefeneces(View view) {
        Intent intent = new Intent(this, PreferenceActivity.class);
        startActivity(intent);
    }

    private void init() {
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        mLanguageCode = sp.getString("lang", "en");
    }

}
