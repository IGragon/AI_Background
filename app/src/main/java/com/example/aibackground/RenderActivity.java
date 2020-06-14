package com.example.aibackground;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.AsyncTask;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.example.aibackground.utils.ImageUtils.*;

public class RenderActivity extends AppCompatActivity {

    protected Uri originalImageUri; // переменные для работы с UI
    protected Bitmap originalImageBitmap;
    protected Bitmap backgroundImage;
    protected Bitmap finalImage;
    protected Bitmap cutImage;
    protected Bitmap maskImage;
    //protected ImageView imageView;
    protected ImageEditView imageEditView;
    protected ImageView loadingView;
    protected ImageButton saveButton;
    protected AnimationDrawable loadingAnimation;


    protected static final int ACTIVITY_GET_IMAGE_FROM_GALLERY = 404;
    private static final int REQUEST_PERMISSIONS = 69;
    protected int imageOrientation;
    protected boolean imageSaved = false;
    protected boolean objectsAreFound = false;
    protected String folderName = "/AI Background/";
    protected String savedImageName;

    protected final int imageSize = 257; // переменные для работы модели tfLite
    protected final int NUM_CLASSES = 21;
    protected final int numOfThreads = 4;
    protected final float IMAGE_MEAN = 128.0f;
    protected final float IMAGE_STD = 128.0f;
    protected Interpreter tflite;
    protected Interpreter.Options tfOptions = new Interpreter.Options();
    protected ByteBuffer imgData;
    protected ByteBuffer segmentationMasks = ByteBuffer.allocateDirect(imageSize * imageSize * NUM_CLASSES * 4).order(ByteOrder.nativeOrder());

    protected static final String MODEL_PATH = "deeplabv3_257_mv_gpu.tflite"; // расположение модели tfLite

    protected MappedByteBuffer loadModelFile(Activity activity) throws IOException { // загрузчик модели
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) { // во время создания активити
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_photo);
        requestRuntimePermissions();

        originalImageUri = Uri.parse(getIntent().getStringExtra("image")); // получение ссылки на изображение из MainActivity
        imageOrientation = getIntent().getIntExtra("orientation", 0);

        //imageView = (ImageView) findViewById(R.id.imageView); // инициализация Ui объектов
        imageEditView = (ImageEditView) findViewById(R.id.imageEditView);
        loadingView = (ImageView) findViewById(R.id.loadingView);
        loadingAnimation = (AnimationDrawable) loadingView.getDrawable();
        saveButton = (ImageButton) findViewById(R.id.buttonSave);

        RenderImage renderImage = new RenderImage(); // создание и запуск отдельного потока для обработки входящего изображения
        renderImage.execute();
    }

    public void backButton(View view) { // возвращение к выбору изображения
        finish();
    }

    public void chooseBackground(View view) { // выбор фона
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        startActivityForResult(Intent.createChooser(intent, getString(R.string.get_image)), ACTIVITY_GET_IMAGE_FROM_GALLERY);
    }

    public void saveFinalImage(View view) { // сохраняем конечное изображение
        saveImage();
    }

    public void shareImage(View view) {
        if (!imageSaved) {
            saveImage();
        }
        if (savedImageName != null) {
            requestRuntimePermissions();
            File imageFile = new File(savedImageName);
            Uri uriToImage = FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", imageFile);

            Intent shareIntent =
                    ShareCompat.IntentBuilder.from(this)
                            .setType("image/jpeg")
                            .setStream(uriToImage)
                            .getIntent();
            shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.sharing_text));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            grantUriPermission(this.getApplicationContext().getPackageName(), uriToImage, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (shareIntent.resolveActivity(getPackageManager()) != null) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("", getString(R.string.sharing_text));
                clipboard.setPrimaryClip(clip);

                Toast.makeText(this, R.string.sharing_label, Toast.LENGTH_LONG).show();
                startActivity(shareIntent);
            }
        }
    }

    protected void saveImage() {
        if (finalImage != null) {
            if (!imageSaved) {
                try {
                    String imageFileName = createImageFileName();
                    Log.d("finalImageFileName", imageFileName);
                    FileOutputStream fos = new FileOutputStream(imageFileName);

                    finalImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.flush();
                    fos.close();

                    ContentValues values = new ContentValues();

                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    values.put(MediaStore.MediaColumns.DATA, imageFileName);

                    this.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                    imageSaved = true;
                    savedImageName = imageFileName;

                    Toast.makeText(this, R.string.image_saved, Toast.LENGTH_SHORT).show();
                    Log.d("PATH", imageFileName);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.error_while_saving_an_image, Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, R.string.error_image_has_been_saved, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.error_saving_image_without_background, Toast.LENGTH_LONG).show();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_GET_IMAGE_FROM_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) { // получаем и обрабатываем задний фон из галереи
            //imageView.setImageBitmap(null);

            Uri uri = data.getData();
            imageEditView.setBackgroundImage(null);
            int backgroundImageOrientation = getImageOrientation(getRealPathFromURI(uri, RenderActivity.this));
            try {
                backgroundImage = MediaStore.Images.Media.getBitmap(RenderActivity.this.getContentResolver(), uri);
                backgroundImage = rotateBitmap(backgroundImage, backgroundImageOrientation);
                backgroundImage = rescaleBackgroundImage(backgroundImage, cutImage);
                imageEditView.setBackgroundImage(backgroundImage);
            }catch (Exception e){
                e.printStackTrace();
            }
            //MakeFinalImage makeFinalImage = new MakeFinalImage(); // создание конечного изображения в отдельным потоке чтобы не замораживать интерфейс
            //makeFinalImage.execute(data);
        }
    }

    protected String createImageFileName() throws IOException { // Создание имени файла сохоанения
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "/AIBG_JPEG_" + timeStamp + "_";
        String externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_DCIM).getAbsolutePath();
        File storageDir = new File(externalFilesDir + folderName);


        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        return storageDir.getAbsolutePath().concat(imageFileName.concat(".jpg"));
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

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { // обработка результатов запроса на разрешения
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED
                    && grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "all permissions are granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "please grant every permission to make app work properly", Toast.LENGTH_LONG).show();
            }
        }
    }

    class RenderImage extends AsyncTask<Void, Void, Void> { // поток для обработки входящего изображения
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            loadingView.setVisibility(View.VISIBLE); // запускаем анимацию
            loadingAnimation.start();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d("RenderImage", "doInBackground: start");
            try { // пытаемся загрузить модель
                //tfOptions.addDelegate(new GpuDelegate());
                tfOptions.setNumThreads(numOfThreads);
                tflite = new Interpreter(loadModelFile(RenderActivity.this), tfOptions);
                Log.d("tfLite", "has loaded");
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(RenderActivity.this, "Error while loading model", Toast.LENGTH_LONG).show();
                finish();
            }

            try { // пытаемся создать все нужное для работы модели
                originalImageBitmap = MediaStore.Images.Media.getBitmap(RenderActivity.this.getContentResolver(), originalImageUri);
                originalImageBitmap = cropToSmallerSize(originalImageBitmap);
                originalImageBitmap = rotateBitmap(originalImageBitmap, imageOrientation);
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalImageBitmap, imageSize, imageSize, false);

                imgData = convertBitmapToByteBuffer(scaledBitmap, imageSize, IMAGE_MEAN, IMAGE_STD);
                Log.d("imaData", "has created");
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(RenderActivity.this, "Cannot convert bitmap to byteBuffer", Toast.LENGTH_LONG).show();
                finish();
            }

            tflite.run(imgData, segmentationMasks); // запускаем модель
            ModelResultReader data = new ModelResultReader(segmentationMasks, imageSize, NUM_CLASSES);
            objectsAreFound = data.objectsAreFound;
            if (objectsAreFound){
                maskImage = data.maskImage;
                maskImage = Bitmap.createScaledBitmap(maskImage, originalImageBitmap.getWidth(), originalImageBitmap.getHeight(), false);

                cutImage = layMaskOnImage(maskImage, originalImageBitmap);
                return null;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            loadingAnimation.stop(); // останавливаем анимацию
            loadingView.setVisibility(View.GONE);
            if (objectsAreFound) {
                //imageView.setImageBitmap(cutImage); // ставим вырезанное изображение
                imageEditView.setCutImage(cutImage);
            }else{
                Toast.makeText(RenderActivity.this, R.string.error_no_objects_found,Toast.LENGTH_LONG).show();
                finish();
            }
            Log.d("RenderImage", "onPostExecute: finish");
        }
    }

    class MakeFinalImage extends AsyncTask<Intent, Void, Void> { // поток для обработки конечного изображения
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingView.setVisibility(View.VISIBLE); // начинаем анимацию
            loadingAnimation.start();
        }

        @Override
        protected Void doInBackground(Intent... intents) {
            Log.d("MakeFinalImage", "doInBackground: start");
            Uri uri = intents[0].getData();
            try { // обрабатываем фон
                int backgroundImageOrientation = getImageOrientation(getRealPathFromURI(uri, RenderActivity.this));
                backgroundImage = MediaStore.Images.Media.getBitmap(RenderActivity.this.getContentResolver(), uri);
                backgroundImage = rotateBitmap(backgroundImage, backgroundImageOrientation);
                backgroundImage = rescaleBackgroundImage(backgroundImage, cutImage);
            } catch (Exception e) {
                e.printStackTrace();
            }
            finalImage = combineCutImageAndBackgroundImage(cutImage, backgroundImage);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            imageSaved = false;

            loadingAnimation.stop(); // останавливаем анимацию
            loadingView.setVisibility(View.GONE);

            //imageView.setImageBitmap(finalImage);

            Log.d("MakeFinalImage", "onPostExecute: finish");
        }
    }
}
