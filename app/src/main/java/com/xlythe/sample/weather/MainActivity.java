package com.xlythe.sample.weather;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.xlythe.service.weather.AwarenessWeather;
import com.xlythe.service.weather.AwarenessWeatherService;
import com.xlythe.service.weather.OpenWeatherService;
import com.xlythe.service.weather.PermissionUtils;
import com.xlythe.service.weather.Weather;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
    };
    private static final int REQUEST_CODE_PERMISSIONS = 1;

    @SuppressWarnings("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.sync).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Sending broadcast action=" + AwarenessWeatherService.ACTION_RUN_MANUALLY);
                Intent intent = new Intent(getBaseContext(), AwarenessWeatherService.class);
                intent.setAction(AwarenessWeatherService.ACTION_RUN_MANUALLY);
                startService(intent);
            }
        });

        if (!PermissionUtils.hasPermissions(this, REQUIRED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        } else {
            AwarenessWeatherService.schedule(this);

            Weather weather = new AwarenessWeather();
            weather.restore(this);

            TextView textView = (TextView) findViewById(R.id.weather);
            textView.setText(weather.toString());
        }
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
