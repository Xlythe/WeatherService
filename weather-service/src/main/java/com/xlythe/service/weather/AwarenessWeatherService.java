package com.xlythe.service.weather;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.RequiresPermission;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

/**
 * A service that queries the GoogleApiClient for the latest weather information
 */
public class AwarenessWeatherService extends WeatherService {
    public static final String ACTION_DATA_CHANGED = "com.xlythe.service.weather.AWARENESS_DATA_CHANGED";

    private static final long FREQUENCY_WEATHER = 15 * 60; // 15min in seconds
    private static final long FLEX = 5 * 60; // 5min in seconds

    private static final String BUNDLE_SCHEDULED = "scheduled";
    private static final String BUNDLE_SCHEDULE_TIME = "schedule_time";
    private static final String BUNDLE_FREQUENCY = "frequency";

    public static void setFrequency(Context context, long frequencyInMillis) {
        getSharedPreferences(context).edit()
                .putBoolean(BUNDLE_SCHEDULED, true)
                .putLong(BUNDLE_FREQUENCY, frequencyInMillis)
                .apply();
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    public static void schedule(Context context) {
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);
        PeriodicTask task = new PeriodicTask.Builder()
                .setService(AwarenessWeatherService.class)
                .setTag(AwarenessWeatherService.class.getSimpleName())
                .setPeriod(getSharedPreferences(context).getLong(BUNDLE_FREQUENCY, FREQUENCY_WEATHER * 1000) / 1000)
                .setFlex(FLEX)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .build();
        gcmNetworkManager.schedule(task);
        getSharedPreferences(context).edit()
                .putBoolean(BUNDLE_SCHEDULED, true)
                .putLong(BUNDLE_SCHEDULE_TIME, System.currentTimeMillis())
                .apply();
    }

    public static void cancel(Context context) {
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);
        gcmNetworkManager.cancelTask(AwarenessWeatherService.class.getSimpleName(), AwarenessWeatherService.class);
        getSharedPreferences(context).edit().putBoolean(BUNDLE_SCHEDULED, false).apply();
    }

    public static boolean isScheduled(Context context) {
        return getSharedPreferences(context).getBoolean(BUNDLE_SCHEDULED, false);
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(AwarenessWeatherService.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    @Override
    protected String getApiKey() {
        return null;
    }

    @Override
    protected boolean isScheduled() {
        return isScheduled(this);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    @Override
    protected void schedule(String apiKey) {
        schedule(this);
    }

    @Override
    protected void cancel() {
        cancel(this);
    }

    @Override
    public int onRunTask(final TaskParams params) {
        AwarenessWeather weather = new AwarenessWeather();
        if (!weather.fetch(this)) {
            return GcmNetworkManager.RESULT_RESCHEDULE;
        }
        weather.save(this);

        broadcast(ACTION_DATA_CHANGED);

        return GcmNetworkManager.RESULT_SUCCESS;
    }
}
