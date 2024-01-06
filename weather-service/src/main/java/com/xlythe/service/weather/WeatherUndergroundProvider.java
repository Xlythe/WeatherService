package com.xlythe.service.weather;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

public class WeatherUndergroundProvider extends WeatherProvider {
    private final String mApiKey;

    public WeatherUndergroundProvider(Context context, String apiKey) {
        super(context);
        mApiKey = apiKey;
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET
    })
    @Override
    public void runImmediately() {
        WeatherUndergroundService.runImmediately(getContext());
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    @Override
    public void schedule() {
        WeatherUndergroundService.schedule(getContext(), mApiKey);
    }

    @Override
    public void cancel() {
        WeatherUndergroundService.cancel(getContext());
    }

    @Override
    public boolean isScheduled() {
        return WeatherUndergroundService.isScheduled(getContext());
    }

    @Override
    public Weather getWeather() {
        return new WeatherUnderground(getContext());
    }

    @Override
    public void registerReceiver(BroadcastReceiver broadcastReceiver) {
        ContextCompat.registerReceiver(getContext(), broadcastReceiver, new IntentFilter(WeatherUndergroundService.ACTION_DATA_CHANGED), ContextCompat.RECEIVER_EXPORTED);
    }
}
