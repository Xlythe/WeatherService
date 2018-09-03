package com.xlythe.service.weather;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import androidx.annotation.RequiresPermission;

public class AwarenessWeatherProvider extends WeatherProvider {
    public AwarenessWeatherProvider(Context context) {
        super(context);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION
    })
    @Override
    public void runImmediately() {
        AwarenessWeatherService.runImmediately(getContext());
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    @Override
    public void schedule() {
        AwarenessWeatherService.schedule(getContext());
    }

    @Override
    public void cancel() {
        AwarenessWeatherService.cancel(getContext());
    }

    @Override
    public boolean isScheduled() {
        return AwarenessWeatherService.isScheduled(getContext());
    }

    @Override
    public Weather getWeather() {
        return new AwarenessWeather(getContext());
    }

    @Override
    public void registerReceiver(BroadcastReceiver broadcastReceiver) {
        getContext().registerReceiver(broadcastReceiver, new IntentFilter(AwarenessWeatherService.ACTION_DATA_CHANGED));
    }
}
