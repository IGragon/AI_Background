package com.example.aibackground.utils;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;

public class ImageUtils {

    private static final String UNKNOWN_FILE_PATH = "INVALID URI";

    private final static String[] labels = {"background", "aeroplane", "bicycle", "bird", "boat", // все объекты, которые может распознавать модель
            "bottle", "bus", "car", "cat", "chair", "cow", "dining table", "dog", "horse",
            "motorbike", "person", "potted plant", "sheep", "sofa", "train", "tv"};

    public static ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap, int imageSize, float IMAGE_MEAN, float IMAGE_STD) { // конвертируем Bitmap в ByteBuffer
        int[] intValues = new int[bitmap.getWidth() * bitmap.getHeight()];
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(imageSize * imageSize * 3 * 4).order(ByteOrder.nativeOrder());
        byteBuffer.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0,
                bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < imageSize; ++i) {
            for (int j = 0; j < imageSize; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                byteBuffer.putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                byteBuffer.putFloat((((val) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }
        Log.d("Bitmap to bytebuffer", String.valueOf(byteBuffer != null));

        return byteBuffer;
    }

    public static Bitmap convertBytebufferMaskToBitmap(ByteBuffer byteBuffer, int imageSize, int NUM_CLASSES) { // конвертируем ByteBuffer в Bitmap
        Bitmap maskImage = Bitmap.createBitmap(imageSize, imageSize, Bitmap.Config.ARGB_8888);
        int[][] mSegmentBits = new int[imageSize][imageSize];
        HashSet<Integer> itemsFound = new HashSet<>();

        byteBuffer.rewind();

        for (int y = 0; y < imageSize; ++y) {
            for (int x = 0; x < imageSize; ++x) {
                float maxVal = 0f;
                mSegmentBits[x][y] = 0;

                for (int c = 0; c < NUM_CLASSES; c++) {
                    float value = byteBuffer.getFloat((y * imageSize * NUM_CLASSES + x * NUM_CLASSES + c) * 4);
                    if (c == 0 || value > maxVal) {
                        maxVal = value;
                        mSegmentBits[x][y] = c;
                    }
                }

                itemsFound.add(mSegmentBits[x][y]);
                if (mSegmentBits[x][y] == 0) {
                    maskImage.setPixel(x, y, Color.TRANSPARENT);
                } else if (mSegmentBits[x][y] == 15 /*|| mSegmentBits[x][y] == 8 || mSegmentBits[x][y] == 12*/) {
                    maskImage.setPixel(x, y, Color.BLACK);
                }
            }
        }

        return maskImage;
    }

    public static Bitmap layMaskOnImage(Bitmap maskBitmap, Bitmap bitmap) { // накладываем маску на изображение
        Bitmap resultBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        for (int y = 0; y < bitmap.getHeight(); ++y) {
            for (int x = 0; x < bitmap.getWidth(); ++x) {
                if (maskBitmap.getPixel(x, y) == 0) {
                    resultBitmap.setPixel(x, y, maskBitmap.getPixel(x, y));
                } else {
                    resultBitmap.setPixel(x, y, bitmap.getPixel(x, y));
                }
            }
        }

        return resultBitmap;
    }

    public static Bitmap combineCutImageAndBackgroundImage(Bitmap cutBitmap, Bitmap backgroundBitmap) { // совмещаем фон и вырезанную картинку
        int ct_midWidth = cutBitmap.getWidth() / 2;
        int ct_midHeight = cutBitmap.getHeight() / 2;

        int bg_midWidth = backgroundBitmap.getWidth() / 2;
        int bg_midHeight = backgroundBitmap.getHeight() / 2;

        for (int y = 0; y < cutBitmap.getHeight(); ++y) {
            for (int x = 0; x < cutBitmap.getWidth(); ++x) {
                if (cutBitmap.getPixel(x, y) != 0) {
                    backgroundBitmap.setPixel(bg_midWidth - ct_midWidth + x, bg_midHeight - ct_midHeight + y, cutBitmap.getPixel(x, y));
                }
            }
        }

        return backgroundBitmap;
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int rotate) { // поворачиваем Bitmap
        Matrix matrix = new Matrix();
        matrix.postRotate(rotate);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Bitmap cropToSmallerSize(Bitmap bitmap) { // если изображение слишком большое, что сводим его к формату 1080 на что-нибудь
        Bitmap resultBitmap;
        if (bitmap.getWidth() > 1080 || bitmap.getHeight() > 1080) {
            float ratio = (float) bitmap.getWidth() / (float) bitmap.getHeight();
            if (bitmap.getWidth() > bitmap.getHeight()) {
                resultBitmap = Bitmap.createScaledBitmap(bitmap, 1080, (int) ((float) 1080 / ratio), false);
            } else {
                resultBitmap = Bitmap.createScaledBitmap(bitmap, (int) ((float) 1080 * ratio), 1080, false);
            }
            return resultBitmap;
        } else {
            return bitmap;
        }
    }

    public static Bitmap rescaleBackgroundImage(Bitmap backgroundBitmap, Bitmap objectBitmap) { // изменяем размеры фона
        float bg_ratio = (float) backgroundBitmap.getWidth() / (float) backgroundBitmap.getHeight();
        int bg_width, bg_height;

        if (backgroundBitmap.getWidth() > backgroundBitmap.getHeight()) {
            if (objectBitmap.getWidth() > objectBitmap.getHeight()) {
                backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmap, objectBitmap.getWidth(), objectBitmap.getHeight(), false);
                return backgroundBitmap;
            } else {
                bg_height = objectBitmap.getHeight();
                bg_width = (int) (bg_height * bg_ratio);
            }
        } else {
            if (objectBitmap.getWidth() < objectBitmap.getHeight()) {
                backgroundBitmap = Bitmap.createScaledBitmap(backgroundBitmap, objectBitmap.getWidth(), objectBitmap.getHeight(), false);
                return backgroundBitmap;
            } else {
                bg_width = objectBitmap.getWidth();
                bg_height = (int) ((float) bg_width / bg_ratio);
            }
        }

        return Bitmap.createScaledBitmap(backgroundBitmap, bg_width, bg_height, false);
    }

    public static String getRealPathFromGalleryURI(Uri contentUri, Context context) { // получаем реальный путь, если файл пришел из галереи
        try {
            Log.d("ImageUtils", "content Uri: " + contentUri.toString());
            String wholeID = DocumentsContract.getDocumentId(contentUri);
            String id = wholeID.split(":")[1];
            String[] column = {MediaStore.Images.Media.DATA};
            String sel = MediaStore.Images.Media._ID + "=?";
            Cursor cursor = context.getContentResolver().
                    query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            column, sel, new String[]{id}, null);

            String filePath = "";
            int columnIndex = cursor.getColumnIndex(column[0]);

            if (cursor.moveToFirst()) {
                filePath = cursor.getString(columnIndex);
            }

            cursor.close();
            return filePath;
        } catch (Exception e) {
            return UNKNOWN_FILE_PATH;
        }
    }

    public static int getImageOrientation(String imagePath) { // получаем ориентацию изображения
        if (imagePath.equals(UNKNOWN_FILE_PATH)) {
            return 0;
        }
        try {
            ExifInterface exifInterface = new ExifInterface(imagePath);
            switch (exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}
