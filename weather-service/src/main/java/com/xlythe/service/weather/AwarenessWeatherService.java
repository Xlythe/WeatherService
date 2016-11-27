package com.xlythe.service.weather;

import android.Manifest;
import android.content.Context;
import android.support.annotation.RequiresPermission;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

/**
 * A service that queries the GoogleApiClient for the latest weather information
 */
public class AwarenessWeatherService extends GcmTaskService {
    private static final long FREQUENCY_WEATHER = 3 * 60 * 60; // 3 hours in seconds
    private static final long FLEX = 30 * 60; // 30min in seconds

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION
    })
    public static void schedule(Context context) {
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);
        PeriodicTask task = new PeriodicTask.Builder()
                .setService(AwarenessWeatherService.class)
                .setTag(AwarenessWeatherService.class.getSimpleName())
                .setPeriod(FREQUENCY_WEATHER)
                .setFlex(FLEX)
                .setPersisted(true)
                .build();
        gcmNetworkManager.schedule(task);
    }

    @Override
    public int onRunTask(final TaskParams params) {
        AwarenessWeather weather = new AwarenessWeather();
        if (!weather.fetch(this)) {
            return GcmNetworkManager.RESULT_RESCHEDULE;
        }
        weather.save(this);
        return GcmNetworkManager.RESULT_SUCCESS;
    }
}
