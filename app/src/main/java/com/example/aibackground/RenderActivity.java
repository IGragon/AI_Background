package com.example.aibackground;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import java.util.Random;

import static com.example.aibackground.utils.ImageUtils.*;

public class RenderActivity extends AppCompatActivity {

    private Uri originalImageUri;
    private Bitmap originalImageBitmap;
    private Bitmap backgroundImage;
    private Bitmap finalImage;
    private Bitmap cutImage;
    private Bitmap maskImage;
    private Random random = new Random();
    private ImageView imageView;
    private Button saveButton;


    private static final int ACTIVITY_GET_IMAGE_FROM_GALLERY = 404;
    private int imageOrientation;

    private final int imageSize = 257;
    private final int NUM_CLASSES = 21;
    private final int numOfThreads = 4;
    private final float IMAGE_MEAN = 128.0f;
    private final float IMAGE_STD = 128.0f;
    private int[] colors = new int[NUM_CLASSES];
    private int[] lablesFound = new int[NUM_CLASSES];
    private Interpreter tflite;
    private Interpreter.Options tfOptions = new Interpreter.Options();
    private ByteBuffer imgData;
    private ByteBuffer segmentationMasks = ByteBuffer.allocateDirect(imageSize * imageSize * NUM_CLASSES * 4).order(ByteOrder.nativeOrder());


    private final static String[] labels = {"background", "aeroplane", "bicycle", "bird", "boat", // все объекты, которые может распознавать модель
            "bottle", "bus", "car", "cat", "chair", "cow", "dining table", "dog", "horse",
            "motorbike", "person", "potted plant", "sheep", "sofa", "train", "tv"};

    private static final String MODEL_PATH = "deeplabv3_257_mv_gpu.tflite";

    private MappedByteBuffer loadModelFile(Activity activity) throws IOException { // загрузчик модели
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
        makeRandomColors();

        originalImageUri = Uri.parse(getIntent().getStringExtra("image")); // получение ссылки на изображение из MainActivity
        imageOrientation = getIntent().getIntExtra("orientation", 0);
        imageView = (ImageView) findViewById(R.id.imageView);
        saveButton = (Button) findViewById(R.id.save_button);

        try { // пытаемся загрузить модель
            tfOptions.addDelegate(new GpuDelegate());
            tfOptions.setNumThreads(numOfThreads);
            tflite = new Interpreter(loadModelFile(this), tfOptions);
            Log.d("tfLite", "has loaded");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Model is not found", Toast.LENGTH_LONG).show();
            finish();
        }

        try { // пытаемся создать все нужное для работы модели
            originalImageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), originalImageUri);
            originalImageBitmap = cropToSmallerSize(originalImageBitmap);
            originalImageBitmap = rotateBitmap(originalImageBitmap, imageOrientation);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalImageBitmap, imageSize, imageSize, false);

            imgData = convertBitmapToByteBuffer(scaledBitmap, imageSize, IMAGE_MEAN, IMAGE_STD);
            Log.d("imaData", "has created");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Cannot convert bitmap to byteBuffer", Toast.LENGTH_LONG).show();
            finish();
        }

        tflite.run(imgData, segmentationMasks); // запускаем модель
        maskImage = convertBytebufferMaskToBitmap(segmentationMasks, imageSize, NUM_CLASSES, colors);
        maskImage = Bitmap.createScaledBitmap(maskImage, originalImageBitmap.getWidth(), originalImageBitmap.getHeight(), false);

        cutImage = layMaskOnImage(maskImage, originalImageBitmap);
        imageView.setImageBitmap(cutImage);

        for (int i = 0; i < NUM_CLASSES; ++i) {
            if (lablesFound[i] > 0) {
                Log.d("Item found", labels[i]);
            }
        }
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

            ContentValues values = new ContentValues();

            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.DATA, imageFileName);

            this.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            Toast.makeText(this, "Image successfully saved", Toast.LENGTH_LONG).show();
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
            Uri uri = data.getData();
            try {
                int backgroundImageOrientation = getImageOrientation(getRealPathFromGalleryURI(uri));
                backgroundImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                backgroundImage = rotateBitmap(backgroundImage, backgroundImageOrientation);

                backgroundImage = Bitmap.createScaledBitmap(backgroundImage, cutImage.getWidth(), cutImage.getHeight(), false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            finalImage = combineCutImageAndBackgroundImage(cutImage, backgroundImage);
            imageView.setImageBitmap(finalImage);
        }
        if (finalImage != null) {
            saveButton.setEnabled(true);
        } else {
            saveButton.setEnabled(false);
        }
    }

    private String createImageFileName() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "AIBG_JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_DCIM);
        /*File image = File.createTempFile(
                imageFileName,  *//* префикс *//*
                ".jpg",         *//* суффикс *//*
                storageDir      *//* директория *//*
        );*/

        return storageDir.getAbsolutePath().concat(imageFileName.concat(".jpg"));
    }

    private String getRealPathFromGalleryURI(Uri contentUri) {
        String wholeID = DocumentsContract.getDocumentId(contentUri);
        String id = wholeID.split(":")[1];
        String[] column = { MediaStore.Images.Media.DATA };
        String sel = MediaStore.Images.Media._ID + "=?";
        Cursor cursor = getContentResolver().
                query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        column, sel, new String[]{ id }, null);

        String filePath = "";
        int columnIndex = cursor.getColumnIndex(column[0]);

        if (cursor.moveToFirst()) {
            filePath = cursor.getString(columnIndex);
        }

        cursor.close();
        return filePath;
    }

    private void makeRandomColors() {
        colors[0] = Color.TRANSPARENT;
        for (int i = 1; i < NUM_CLASSES; ++i) {
            float r = random.nextFloat();
            float g = random.nextFloat();
            float b = random.nextFloat();
            colors[i] = Color.argb((128), r, g, b);
        }
    }
}
