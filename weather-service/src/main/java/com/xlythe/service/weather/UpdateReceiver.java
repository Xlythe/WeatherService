package com.xlythe.service.weather;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * When an APK is updated, all of its jobs are unscheduled. We'll listen for updates and reschedule
 * them as necessary.
 */
public class UpdateReceiver extends BroadcastReceiver {
    private static final String TAG = UpdateReceiver.class.getSimpleName();

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @SuppressWarnings("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        if (OpenWeatherService.isScheduled(context)) {
            Log.v(TAG, "Rescheduling OpenWeatherService");
            OpenWeatherService.schedule(context, OpenWeatherService.getApiKey(context));
        }
        if (WeatherUndergroundService.isScheduled(context)) {
            Log.v(TAG, "Rescheduling WeatherUndergroundService");
            WeatherUndergroundService.schedule(context, WeatherUndergroundService.getApiKey(context));
        }
        if (PirateWeatherService.isScheduled(context)) {
            Log.v(TAG, "Rescheduling PirateWeatherService");
            PirateWeatherService.schedule(context, PirateWeatherService.getApiKey(context));
        }
    }
}
