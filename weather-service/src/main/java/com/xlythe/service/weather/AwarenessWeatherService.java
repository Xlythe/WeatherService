package com.xlythe.service.weather;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.RequiresPermission;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

/**
 * A service that queries the GoogleApiClient for the latest weather information
 */
public class AwarenessWeatherService extends GcmTaskService {
    public static final String ACTION_DATA_CHANGED = "com.xlythe.service.weather.AWARENESS_DATA_CHANGED";

    private static final long FREQUENCY_WEATHER = 3 * 60 * 60; // 3 hours in seconds
    private static final long FLEX = 30 * 60; // 30min in seconds

    private static final String BUNDLE_SCHEDULED = "scheduled";

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION
    })
    public static void schedule(Context context) {
        context = context.getApplicationContext();
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);
        PeriodicTask task = new PeriodicTask.Builder()
                .setService(AwarenessWeatherService.class)
                .setTag(AwarenessWeatherService.class.getSimpleName())
                .setPeriod(FREQUENCY_WEATHER)
                .setFlex(FLEX)
                .setPersisted(true)
                .build();
        gcmNetworkManager.schedule(task);
        getSharedPreferences(context).edit().putBoolean(BUNDLE_SCHEDULED, true).apply();
    }

    public static void cancel(Context context) {
        context = context.getApplicationContext();
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);
        gcmNetworkManager.cancelTask(AwarenessWeatherService.class.getSimpleName(), AwarenessWeatherService.class);
        getSharedPreferences(context).edit().putBoolean(BUNDLE_SCHEDULED, false).apply();
    }

    public static boolean isScheduled(Context context) {
        context = context.getApplicationContext();
        return getSharedPreferences(context).getBoolean(BUNDLE_SCHEDULED, false);
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(AwarenessWeatherService.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    @Override
    public int onRunTask(final TaskParams params) {
        AwarenessWeather weather = new AwarenessWeather();
        if (!weather.fetch(this)) {
            return GcmNetworkManager.RESULT_RESCHEDULE;
        }
        weather.save(this);

        broadcast();

        return GcmNetworkManager.RESULT_SUCCESS;
    }

    private void broadcast() {
        Intent intent = new Intent(ACTION_DATA_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @SuppressWarnings("MissingPermission")
    @Override
    public void onInitializeTasks() {
        if (AwarenessWeatherService.isScheduled(this)) {
            AwarenessWeatherService.schedule(this);
        }
    }
}
