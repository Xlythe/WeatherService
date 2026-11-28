package com.xlythe.service.weather;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import org.json.JSONException;

import java.util.concurrent.TimeUnit;

/**
 * Query open weather map for current weather conditions
 */
public class PirateWeatherService extends LocationBasedService {
    private static final String TAG = PirateWeatherService.class.getSimpleName();

    public static final String ACTION_DATA_CHANGED = "com.xlythe.service.weather.PIRATE_WEATHER_DATA_CHANGED";

    private static final String TAG_WEATHER = PirateWeatherService.class.getSimpleName() + "_weather";
    private static final int FREQUENCY_WEATHER = 2 * 60 * 60; // 2hrs in seconds
    private static final int FLEX_WEATHER = 30 * 60; // 30min in seconds

    private static final String BUNDLE_SCHEDULED = "scheduled";
    private static final String BUNDLE_SCHEDULE_TIME = "schedule_time";
    private static final String BUNDLE_API_KEY = "api_key";
    private static final String BUNDLE_TAG = "tag";
    private static final String BUNDLE_FREQUENCY = "frequency";


    public PirateWeatherService(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET
    })
    public static void runImmediately(Context context) {
        runImmediately(context, PirateWeatherService.class, null);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    public static void schedule(Context context, String apiKey) {
        if (DEBUG) Log.d(TAG, "Scheduling PirateWeather api");
        setApiKey(context, apiKey);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        TAG_WEATHER,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        new PeriodicWorkRequest.Builder(PirateWeatherService.class, getFrequency(context), TimeUnit.SECONDS)
                                .setConstraints(constraints)
                                .setInputData(new Data.Builder()
                                        .putString(BUNDLE_TAG, TAG_WEATHER)
                                        .build())
                                .addTag(TAG_WEATHER)
                                .build());

        getSharedPreferences(context).edit()
                .putBoolean(BUNDLE_SCHEDULED, true)
                .putLong(BUNDLE_SCHEDULE_TIME, System.currentTimeMillis())
                .apply();
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(TAG_WEATHER);
        getSharedPreferences(context).edit().putBoolean(BUNDLE_SCHEDULED, false).apply();
    }

    public static boolean isScheduled(Context context) {
        return getSharedPreferences(context).getBoolean(BUNDLE_SCHEDULED, false);
    }

    static void setApiKey(Context context, String apiKey) {
        getSharedPreferences(context).edit().putString(BUNDLE_API_KEY, apiKey).apply();
    }

    public static String getApiKey(Context context) {
        return getSharedPreferences(context).getString(BUNDLE_API_KEY, null);
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
        Weather weather = new PirateWeather();
        weather.restore(context);
        return isFresh(weather.getLastUpdate(), System.currentTimeMillis(), getFrequency(context), FLEX_WEATHER);
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PirateWeatherService.class.getSimpleName(), Context.MODE_PRIVATE);
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
        // Extras are null when running manually.
        if (extras == null) {
            Bundle weatherMetadata = new Bundle();
            weatherMetadata.putString(BUNDLE_TAG, TAG_WEATHER);
            return super.onRunTask(weatherMetadata);
        } else if (TAG_WEATHER.equals(extras.getString(BUNDLE_TAG))
                && hasRunRecently(getContext())) {
            return Result.SUCCESS;
        }

        return super.onRunTask(extras);
    }

    @Override
    protected String createUrl(double latitude, double longitude) {
        return forecastUrl(getApiKey(), latitude, longitude);
    }

    static String forecastUrl(String key, double latitude, double longitude) {
        return Uri.parse("https://api.pirateweather.net/forecast").buildUpon()
                .appendPath(key).appendEncodedPath(latitude + "," + longitude)
                .appendQueryParameter("units", "si")
                .appendQueryParameter("exclude", "minutely,hourly,alerts").build().toString();
    }

    @Override
    protected void parse(String json, Location location) throws JSONException {
        PirateWeather weather = new PirateWeather();
        weather.restore(getContext());
        if (!weather.fetch(getContext(), json)) {
            throw new JSONException("Failed to parse data");
        }
        weather.setLocation(location.getLatitude(), location.getLongitude());
        weather.save(getContext());
        broadcast(ACTION_DATA_CHANGED);
    }
}
