package com.xlythe.sample.weather;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.xlythe.service.weather.AwarenessWeatherProvider;
import com.xlythe.service.weather.PermissionUtils;
import com.xlythe.service.weather.WeatherProvider;

public class MainActivity extends AppCompatActivity {
    private static final String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
    };
    private static final int REQUEST_CODE_PERMISSIONS = 1;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            invalidate();
        }
    };

    private WeatherProvider mWeatherProvider = new AwarenessWeatherProvider(this);

    @SuppressWarnings("MissingPermission")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.sync).setOnClickListener(v -> {
            mWeatherProvider.runImmediately();
            invalidate();
        });

        if (!PermissionUtils.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        } else {
            if (!mWeatherProvider.isScheduled()) {
                mWeatherProvider.schedule();
            }
            invalidate();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mWeatherProvider.registerReceiver(mReceiver);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(mReceiver);
        super.onStop();
    }

    private void invalidate() {
        TextView textView = findViewById(R.id.weather);
        textView.setText(mWeatherProvider.getWeather().toString());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (REQUEST_CODE_PERMISSIONS == requestCode) {
            recreate();
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
