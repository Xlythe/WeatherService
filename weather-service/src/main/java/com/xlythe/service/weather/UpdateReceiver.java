package com.xlythe.service.weather;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * When an APK is updated, all of its jobs are unscheduled. We'll listen for updates and reschedule
 * them as necessary.
 */
public class UpdateReceiver extends BroadcastReceiver {
    @SuppressWarnings("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            if (AwarenessWeatherService.isScheduled(context)) {
                AwarenessWeatherService.schedule(context);
            }
            if (OpenWeatherService.isScheduled(context)) {
                OpenWeatherService.schedule(context, OpenWeatherService.getApiKey(context));
            }
            if (WeatherUndergroundService.isScheduled(context)) {
                WeatherUndergroundService.schedule(context, WeatherUndergroundService.getApiKey(context));
            }
        }
    }
}
