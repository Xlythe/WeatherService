package com.xlythe.service.weather;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Query open weather map for current weather conditions
 */
public class OpenWeatherService extends LocationBasedService {
    private static final String TAG = OpenWeatherService.class.getSimpleName();

    public static final String ACTION_DATA_CHANGED = "com.xlythe.service.weather.OPEN_WEATHER_DATA_CHANGED";

    private static final int FREQUENCY_WEATHER = 2 * 60 * 60; // 2hrs in seconds
    private static final int FLEX = 30 * 60; // 30min in seconds

    private static final String BUNDLE_SCHEDULED = "scheduled";
    private static final String BUNDLE_SCHEDULE_TIME = "schedule_time";
    private static final String BUNDLE_API_KEY = "api_key";
    private static final String BUNDLE_FREQUENCY = "frequency";

    private static final String URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final String PARAM_LAT = "lat";
    private static final String PARAM_LNG = "lon";
    private static final String PARAM_API_KEY = "appid";

    public OpenWeatherService(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET
    })
    public static void runImmediately(Context context) {
        runImmediately(context, OpenWeatherService.class, null);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    public static void schedule(Context context, String apiKey) {
        if (DEBUG) Log.d(TAG, "Scheduling OpenWeather api");
        getSharedPreferences(context).edit().putString(BUNDLE_API_KEY, apiKey).apply();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(OpenWeatherService.class, getFrequency(context), TimeUnit.SECONDS)
                        .setConstraints(constraints)
                        .addTag(TAG)
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request);

        getSharedPreferences(context).edit()
                .putBoolean(BUNDLE_SCHEDULED, true)
                .putLong(BUNDLE_SCHEDULE_TIME, System.currentTimeMillis())
                .apply();
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(TAG);
        getSharedPreferences(context).edit().putBoolean(BUNDLE_SCHEDULED, false).apply();
    }

    public static boolean isScheduled(Context context) {
        return getSharedPreferences(context).getBoolean(BUNDLE_SCHEDULED, false);
    }

    public static String getApiKey(Context context) {
        return getSharedPreferences(context).getString(BUNDLE_API_KEY, null);
    }

    static void setApiKey(Context context, String apiKey) {
        getSharedPreferences(context).edit().putString(BUNDLE_API_KEY, apiKey).apply();
    }

    public static void setFrequency(Context context, long frequencyInMillis) {
        getSharedPreferences(context).edit()
                .putBoolean(BUNDLE_SCHEDULED, true)
                .putLong(BUNDLE_FREQUENCY, frequencyInMillis)
                .apply();
    }

    private static int getFrequency(Context context) {
        return (int) (getSharedPreferences(context).getLong(BUNDLE_FREQUENCY, FREQUENCY_WEATHER * 1000) / 1000);
    }

    private static boolean hasRunRecently(Context context) {
        Weather weather = new OpenWeather();
        weather.restore(context);
        return weather.getLastUpdate() > System.currentTimeMillis() - getFrequency(context) + FLEX;
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(OpenWeatherService.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    @Override
    protected String getApiKey() {
        return getApiKey(getContext());
    }

    @Override
    protected boolean isScheduled() {
        return isScheduled(getContext());
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    @Override
    protected void schedule(String apiKey) {
        schedule(getContext(), apiKey);
    }

    @Override
    protected void cancel() {
        cancel(getContext());
    }

    @Override
    public Result onRunTask(@Nullable Bundle extras) {
        // Extras are null when run manually.
        if (extras != null && hasRunRecently(getContext())) {
            return Result.SUCCESS;
        }

        return super.onRunTask(extras);
    }

    @Override
    protected String createUrl(double latitude, double longitude) {
        return new Builder()
                .url(URL)
                .param(PARAM_LAT, Double.toString(latitude))
                .param(PARAM_LNG, Double.toString(longitude))
                .param(PARAM_API_KEY, getApiKey())
                .build();
    }

    @Override
    protected void parse(String json) throws JSONException {
        OpenWeather weather = new OpenWeather();
        weather.restore(getContext());
        if (!weather.fetch(getContext(), json)) {
            throw new JSONException("Failed to parse data");
        }
        weather.save(getContext());
        broadcast(ACTION_DATA_CHANGED);
    }
}
