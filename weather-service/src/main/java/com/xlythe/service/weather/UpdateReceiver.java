package com.xlythe.service.weather;

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

    @SuppressWarnings("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            if (AwarenessWeatherService.isScheduled(context)) {
                Log.v(TAG, "Rescheduling AwarenessWeatherService");
                AwarenessWeatherService.schedule(context);
            }
            if (OpenWeatherService.isScheduled(context)) {
                Log.v(TAG, "Rescheduling OpenWeatherService");
                OpenWeatherService.schedule(context, OpenWeatherService.getApiKey(context));
            }
            if (WeatherUndergroundService.isScheduled(context)) {
                Log.v(TAG, "Rescheduling WeatherUndergroundService");
                WeatherUndergroundService.schedule(context, WeatherUndergroundService.getApiKey(context));
            }
        }
    }
}
