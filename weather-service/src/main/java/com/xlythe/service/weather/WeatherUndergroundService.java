package com.xlythe.service.weather;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.gcm.TaskParams;

import org.json.JSONException;

/**
 * Query open weather map for current weather conditions
 */
public class WeatherUndergroundService extends LocationBasedService {
    private static final String TAG = WeatherUndergroundService.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final String ACTION_DATA_CHANGED = "com.xlythe.service.weather.WUNDERGROUND_WEATHER_DATA_CHANGED";

    private static final String TAG_WEATHER = "weather";
    private static final long FREQUENCY_WEATHER = 2 * 60 * 60; // 2hrs in seconds
    private static final long FLEX_WEATHER = 30 * 60; // 30min in seconds

    private static final String TAG_ASTRONOMY = "astronomy";
    private static final long FREQUENCY_ASTRONOMY = 23 * 60 * 60; // 23hrs in seconds
    private static final long FLEX_ASTRONOMY = 60 * 60; // 1hr in seconds

    private static final String BUNDLE_SCHEDULED = "scheduled";
    private static final String BUNDLE_SCHEDULE_TIME = "schedule_time";
    private static final String BUNDLE_API_KEY = "api_key";
    private static final String BUNDLE_TAG = "tag";

    private static final String URL_WEATHER = "http://api.wunderground.com/api/%s/geolookup/conditions/q/%s,%s.json"; // apiKey, latitude, longitude
    private static final String URL_ASTRONOMY = "http://api.wunderground.com/api/%s/astronomy/q/%s,%s.json"; // apiKey, latitude, longitude

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    public static void schedule(Context context, String apiKey) {
        if (DEBUG) Log.d(TAG, "Scheduling weather api");
        getSharedPreferences(context).edit().putString(BUNDLE_API_KEY, apiKey).apply();
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);

        Bundle weatherMetadata = new Bundle();
        weatherMetadata.putString(BUNDLE_TAG, TAG_WEATHER);
        gcmNetworkManager.schedule(new PeriodicTask.Builder()
                .setService(WeatherUndergroundService.class)
                .setTag(WeatherUndergroundService.class.getSimpleName() + "_" + TAG_WEATHER)
                .setPeriod(FREQUENCY_WEATHER)
                .setFlex(FLEX_WEATHER)
                .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .setExtras(weatherMetadata)
                .build());

        Bundle astronomyMetadata = new Bundle();
        astronomyMetadata.putString(BUNDLE_TAG, TAG_ASTRONOMY);
        gcmNetworkManager.schedule(new PeriodicTask.Builder()
                .setService(WeatherUndergroundService.class)
                .setTag(WeatherUndergroundService.class.getSimpleName() + "_" + TAG_ASTRONOMY)
                .setPeriod(FREQUENCY_ASTRONOMY)
                .setFlex(FLEX_ASTRONOMY)
                .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .setExtras(astronomyMetadata)
                .build());

        getSharedPreferences(context).edit()
                .putBoolean(BUNDLE_SCHEDULED, true)
                .putLong(BUNDLE_SCHEDULE_TIME, System.currentTimeMillis())
                .apply();
    }

    public static void cancel(Context context) {
        context = context.getApplicationContext();
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);
        gcmNetworkManager.cancelTask(WeatherUndergroundService.class.getSimpleName(), WeatherUndergroundService.class);
        getSharedPreferences(context).edit().putBoolean(BUNDLE_SCHEDULED, false).apply();
    }

    public static boolean isScheduled(Context context) {
        return getSharedPreferences(context).getBoolean(BUNDLE_SCHEDULED, false);
    }

    public static String getApiKey(Context context) {
        return getSharedPreferences(context).getString(BUNDLE_API_KEY, null);
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(WeatherUndergroundService.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    @Override
    protected String getApiKey() {
        return getApiKey(this);
    }

    @Override
    protected boolean isScheduled() {
        return isScheduled(this);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    @Override
    protected void schedule(String apiKey) {
        schedule(this, apiKey);
    }

    @Override
    protected void cancel() {
        cancel(this);
    }

    @Override
    public int onRunTask(final TaskParams params) {
        // Extras are null when running manually. In this special case, we'd like to run both
        // weather and astronomy queries.
        if (params.getExtras() == null) {
            Bundle weatherMetadata = new Bundle();
            weatherMetadata.putString(BUNDLE_TAG, TAG_WEATHER);
            int status = super.onRunTask(new TaskParams(params.getTag(), weatherMetadata));
            if (status != GcmNetworkManager.RESULT_SUCCESS) {
                return status;
            }

            Bundle astronomyMetadata = new Bundle();
            astronomyMetadata.putString(BUNDLE_TAG, TAG_ASTRONOMY);
            return super.onRunTask(new TaskParams(params.getTag(), weatherMetadata));
        }

        return super.onRunTask(params);
    }

    @Override
    protected String createUrl(double latitude, double longitude) {
        if (TAG_ASTRONOMY.equals(getParams().getString(BUNDLE_TAG))) {
            return new Builder()
                    .url(String.format(URL_ASTRONOMY, getApiKey(), latitude, longitude))
                    .build();
        } else {
            return new Builder()
                    .url(String.format(URL_WEATHER, getApiKey(), latitude, longitude))
                    .build();
        }
    }

    @Override
    protected void parse(String json) throws JSONException {
        WeatherUnderground weather = new WeatherUnderground();
        if (!weather.fetch(this, json)) {
            throw new JSONException("Failed to parse data");
        }
        weather.save(this);
        broadcast(ACTION_DATA_CHANGED);
    }
}
