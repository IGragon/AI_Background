package com.example.aibackground;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity { // приветственная активити

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void start(View view) { // запускаем активити для выбора изображения
        Intent chooseImageIntent = new Intent(this, ChooseImageActivity.class);
        startActivity(chooseImageIntent);
    }
}
