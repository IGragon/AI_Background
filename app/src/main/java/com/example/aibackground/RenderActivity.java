package com.example.aibackground;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

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
    protected ImageView imageView;
    protected ImageView loadingView;
    protected ImageButton saveButton;
    protected AnimationDrawable loadingAnimation;


    protected static final int ACTIVITY_GET_IMAGE_FROM_GALLERY = 404;
    protected int imageOrientation;
    protected boolean imageSaved = false;
    protected String folderName = "/AI Background/";

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
        setContentView(R.layout.activity_render);

        originalImageUri = Uri.parse(getIntent().getStringExtra("image")); // получение ссылки на изображение из MainActivity
        imageOrientation = getIntent().getIntExtra("orientation", 0);

        imageView = (ImageView) findViewById(R.id.imageView); // инициализация Ui объектов
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

                    Toast.makeText(this, "Image successfully saved", Toast.LENGTH_SHORT).show();
                    Log.d("PATH", imageFileName);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error while saving an image. Try again", Toast.LENGTH_LONG).show();
                }
            }else{
                Toast.makeText(this, "This image has already been saved", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Please add background to the image to save it", Toast.LENGTH_LONG).show();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_GET_IMAGE_FROM_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) { // получаем и обрабатываем задний фон из галереи
            imageView.setImageBitmap(null);

            MakeFinalImage makeFinalImage = new MakeFinalImage(); // создание конечного изображения в отдельным потоке чтобы не замораживать интерфейс
            makeFinalImage.execute(data);
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
                tfOptions.addDelegate(new GpuDelegate());
                tfOptions.setNumThreads(numOfThreads);
                tflite = new Interpreter(loadModelFile(RenderActivity.this), tfOptions);
                Log.d("tfLite", "has loaded");
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(RenderActivity.this, "Model is not found", Toast.LENGTH_LONG).show();
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
            maskImage = convertBytebufferMaskToBitmap(segmentationMasks, imageSize, NUM_CLASSES);
            maskImage = Bitmap.createScaledBitmap(maskImage, originalImageBitmap.getWidth(), originalImageBitmap.getHeight(), false);

            cutImage = layMaskOnImage(maskImage, originalImageBitmap);

            Log.d("RenderImage", "doInBackground: finish");
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            loadingAnimation.stop(); // останавливаем анимацию
            loadingView.setVisibility(View.GONE);

            imageView.setImageBitmap(cutImage); // ставим вырезанное изображение

            Log.d("RenderImage", "onPostExecute: finish");
        }
    }

    class MakeFinalImage extends AsyncTask<Intent, Void, Void> { // поток для обработки конечного ихображения
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
                int backgroundImageOrientation = getImageOrientation(getRealPathFromGalleryURI(uri, RenderActivity.this));
                backgroundImage = MediaStore.Images.Media.getBitmap(RenderActivity.this.getContentResolver(), uri);
                backgroundImage = rotateBitmap(backgroundImage, backgroundImageOrientation);
                backgroundImage = rescaleBackgroundImage(backgroundImage, cutImage);
            } catch (Exception e) {
                e.printStackTrace();
            }
            finalImage = combineCutImageAndBackgroundImage(cutImage, backgroundImage);

            Log.d("MakeFinalImage", "doInBackground: finish");
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            imageSaved = false;

            loadingAnimation.stop(); // останавливаем анимацию
            loadingView.setVisibility(View.GONE);

            imageView.setImageBitmap(finalImage);

            Log.d("MakeFinalImage", "onPostExecute: finish");
        }
    }
}
