package com.xlythe.service.weather;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import androidx.annotation.RequiresPermission;

public class OpenWeatherProvider extends WeatherProvider {
    private final String mApiKey;

    public OpenWeatherProvider(Context context, String apiKey) {
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
        OpenWeatherService.runImmediately(getContext());
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    @Override
    public void schedule() {
        OpenWeatherService.schedule(getContext(), mApiKey);
    }

    @Override
    public void cancel() {
        OpenWeatherService.cancel(getContext());
    }

    @Override
    public boolean isScheduled() {
        return OpenWeatherService.isScheduled(getContext());
    }

    @Override
    public Weather getWeather() {
        return new OpenWeather(getContext());
    }

    @Override
    public void registerReceiver(BroadcastReceiver broadcastReceiver) {
        getContext().registerReceiver(broadcastReceiver, new IntentFilter(OpenWeatherService.ACTION_DATA_CHANGED));
    }
}
