package com.xlythe.service.weather;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.Trigger;

import org.json.JSONException;

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

    private static final String URL = "http://api.openweathermap.org/data/2.5/weather";
    private static final String PARAM_LAT = "lat";
    private static final String PARAM_LNG = "lon";
    private static final String PARAM_API_KEY = "appid";

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
        if (DEBUG) Log.d(TAG, "Scheduling weather api");
        getSharedPreferences(context).edit().putString(BUNDLE_API_KEY, apiKey).apply();
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context));
        dispatcher.mustSchedule(dispatcher.newJobBuilder()
                .setService(WeatherUndergroundService.class)
                .setTag(TAG)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(getFrequency(context), getFrequency(context) + FLEX))
                .setConstraints(Constraint.ON_ANY_NETWORK)
                .setLifetime(Lifetime.FOREVER)
                .setReplaceCurrent(true)
                .build());
        getSharedPreferences(context).edit()
                .putBoolean(BUNDLE_SCHEDULED, true)
                .putLong(BUNDLE_SCHEDULE_TIME, System.currentTimeMillis())
                .apply();
    }

    public static void cancel(Context context) {
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context));
        dispatcher.cancel(TAG);
        getSharedPreferences(context).edit().putBoolean(BUNDLE_SCHEDULED, false).apply();
    }

    public static boolean isScheduled(Context context) {
        return getSharedPreferences(context).getBoolean(BUNDLE_SCHEDULED, false);
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
        Weather weather = new WeatherUnderground();
        weather.restore(context);
        return weather.getLastUpdate() > System.currentTimeMillis() - getFrequency(context) + FLEX;
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(OpenWeatherService.class.getSimpleName(), Context.MODE_PRIVATE);
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
    public Result onRunTask(@Nullable Bundle extras) {
        // Extras are null when run manually.
        if (extras != null && hasRunRecently(this)) {
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
        weather.restore(this);
        if (!weather.fetch(this, json)) {
            throw new JSONException("Failed to parse data");
        }
        weather.save(this);
        broadcast(ACTION_DATA_CHANGED);
    }
}
