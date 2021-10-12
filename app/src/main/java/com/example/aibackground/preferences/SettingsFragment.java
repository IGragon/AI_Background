package com.example.aibackground.preferences;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.example.aibackground.MainActivity;
import com.example.aibackground.R;
import com.example.aibackground.utils.LocaleHelper;

import java.util.ArrayList;
import java.util.Locale;



public class SettingsFragment extends PreferenceFragmentCompat {

    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private String mLanguageCode;
    private SharedPreferences sp;
    private SharedPreferences settings;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preference_screen);

        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                sp = PreferenceManager.getDefaultSharedPreferences(getContext());
                SharedPreferences.Editor editor = sp.edit();
                String lang = sp.getString("language_preference", "english").toLowerCase();
                Toast toast = Toast.makeText(getContext(), lang, Toast.LENGTH_SHORT);
                toast.show();
                if (lang != null) {
                    switch (lang) {
                        case "english":
                            mLanguageCode = getResources().getString(R.string.en);
                            editor.putString("lang", mLanguageCode);
                            editor.commit();
                            toast.show();
                            break;
                        case "русский":
                            mLanguageCode = getResources().getString(R.string.ru);
                            editor.putString( "lang", mLanguageCode);
                            editor.commit();
                            toast.show();
                            break;

                        case "française":
                            mLanguageCode = getResources().getString(R.string.fr);
                            editor.putString("lang", mLanguageCode);
                            editor.commit();
                            toast.show();
                            break;
                        case "español":
                            mLanguageCode = getResources().getString(R.string.es);
                            editor.putString( "lang", mLanguageCode);
                            editor.commit();
                            toast.show();
                            break;
                    }
                }
                Intent mStartActivity = new Intent(getContext(), MainActivity.class);
                int mPendingIntentId = 123456;
                PendingIntent mPendingIntent = PendingIntent.getActivity(getContext(), mPendingIntentId,    mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager mgr = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                System.exit(0);
            }
        };

    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    public void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
    }
}
