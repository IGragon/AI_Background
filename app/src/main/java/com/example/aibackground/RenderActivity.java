package com.example.aibackground;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

    protected Uri originalImageUri;
    protected Bitmap originalImageBitmap;
    protected Bitmap backgroundImage;
    protected Bitmap finalImage;
    protected Bitmap cutImage;
    protected Bitmap maskImage;
    protected ImageView imageView;
    protected ImageButton saveButton;


    protected static final int ACTIVITY_GET_IMAGE_FROM_GALLERY = 404;
    protected int imageOrientation;

    protected final int imageSize = 257;
    protected final int NUM_CLASSES = 21;
    protected final int numOfThreads = 4;
    protected final float IMAGE_MEAN = 128.0f;
    protected final float IMAGE_STD = 128.0f;
    protected Interpreter tflite;
    protected Interpreter.Options tfOptions = new Interpreter.Options();
    protected ByteBuffer imgData;
    protected ByteBuffer segmentationMasks = ByteBuffer.allocateDirect(imageSize * imageSize * NUM_CLASSES * 4).order(ByteOrder.nativeOrder());


    protected final static String[] labels = {"background", "aeroplane", "bicycle", "bird", "boat", // все объекты, которые может распознавать модель
            "bottle", "bus", "car", "cat", "chair", "cow", "dining table", "dog", "horse",
            "motorbike", "person", "potted plant", "sheep", "sofa", "train", "tv"};

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
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_render);

        originalImageUri = Uri.parse(getIntent().getStringExtra("image")); // получение ссылки на изображение из MainActivity
        imageOrientation = getIntent().getIntExtra("orientation", 0);
        imageView = (ImageView) findViewById(R.id.imageView);
        saveButton = (ImageButton) findViewById(R.id.buttonSave);

        RenderImage renderImage = new RenderImage();
        renderImage.execute();
    }

    public void backButton(View view) {
        finish();
    }

    public void chooseBackground(View view) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        startActivityForResult(Intent.createChooser(intent, getString(R.string.get_image)), ACTIVITY_GET_IMAGE_FROM_GALLERY);
    }

    public void saveFinalImage(View view) {
        try {
            String imageFileName = createImageFileName();
            FileOutputStream fos = new FileOutputStream(imageFileName);

            finalImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();

            Toast.makeText(this, "File successfully saved", Toast.LENGTH_LONG).show();
            Log.d("PATH", imageFileName);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error while saving an image. Try again", Toast.LENGTH_LONG).show();
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

    protected String createImageFileName() throws IOException { // Создание имени файла
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "AIBG_JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_DCIM);

        return storageDir.getAbsolutePath().concat(imageFileName.concat(".jpg"));
    }

    class RenderImage extends AsyncTask<Void, Void, Void>{ // поток для обработки входящего изображения
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
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
            imageView.setImageBitmap(cutImage);
        }
    }

    class MakeFinalImage extends AsyncTask<Intent, Void, Void>{ //
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Intent... intents) {
            Log.d("MakeFinalImage", "doInBackground: start");
            Uri uri = intents[0].getData();
            try {
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
            imageView.setImageBitmap(finalImage);
            if (finalImage != null) { // включение кнопки сохранения
                saveButton.setEnabled(true);
            } else {
                saveButton.setEnabled(false);
            }
        }
    }
}
