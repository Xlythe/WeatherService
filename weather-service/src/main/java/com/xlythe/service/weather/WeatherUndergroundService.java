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
public class WeatherUndergroundService extends LocationBasedService {
    private static final String TAG = WeatherUndergroundService.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final String ACTION_DATA_CHANGED = "com.xlythe.service.weather.WUNDERGROUND_WEATHER_DATA_CHANGED";

    private static final String TAG_WEATHER = WeatherUndergroundService.class.getSimpleName() + "_weather";
    private static final int FREQUENCY_WEATHER = 2 * 60 * 60; // 2hrs in seconds
    private static final int FLEX_WEATHER = 30 * 60; // 30min in seconds

    private static final String TAG_ASTRONOMY = WeatherUndergroundService.class.getSimpleName() + "_astronomy";
    private static final int FREQUENCY_ASTRONOMY = 23 * 60 * 60; // 23hrs in seconds
    private static final int FLEX_ASTRONOMY = 60 * 60; // 1hr in seconds

    private static final String BUNDLE_SCHEDULED = "scheduled";
    private static final String BUNDLE_SCHEDULE_TIME = "schedule_time";
    private static final String BUNDLE_API_KEY = "api_key";
    private static final String BUNDLE_TAG = "tag";
    private static final String BUNDLE_FREQUENCY = "frequency";

    private static final String URL_WEATHER = "http://api.wunderground.com/api/%s/geolookup/conditions/q/%s,%s.json"; // apiKey, latitude, longitude
    private static final String URL_ASTRONOMY = "http://api.wunderground.com/api/%s/astronomy/q/%s,%s.json"; // apiKey, latitude, longitude

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET
    })
    public static void runImmediately(Context context) {
        runImmediately(context, WeatherUndergroundService.class, null);
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

        Bundle weatherMetadata = new Bundle();
        weatherMetadata.putString(BUNDLE_TAG, TAG_WEATHER);
        dispatcher.mustSchedule(dispatcher.newJobBuilder()
                .setService(WeatherUndergroundService.class)
                .setTag(TAG_WEATHER)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(getFrequency(context), getFrequency(context) + FLEX_WEATHER))
                .setConstraints(Constraint.ON_ANY_NETWORK)
                .setLifetime(Lifetime.FOREVER)
                .setReplaceCurrent(true)
                .setExtras(weatherMetadata)
                .build());

        Bundle astronomyMetadata = new Bundle();
        astronomyMetadata.putString(BUNDLE_TAG, TAG_ASTRONOMY);
        dispatcher.mustSchedule(dispatcher.newJobBuilder()
                .setService(WeatherUndergroundService.class)
                .setTag(TAG_ASTRONOMY)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(FREQUENCY_ASTRONOMY, FREQUENCY_ASTRONOMY + FLEX_ASTRONOMY))
                .setConstraints(Constraint.ON_ANY_NETWORK)
                .setLifetime(Lifetime.FOREVER)
                .setReplaceCurrent(true)
                .setExtras(astronomyMetadata)
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
        return weather.getLastUpdate() > System.currentTimeMillis() - getFrequency(context) + FLEX_WEATHER;
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
    public Result onRunTask(@Nullable Bundle extras) {
        // Extras are null when running manually. In this special case, we'd like to run both
        // weather and astronomy queries.
        if (extras == null) {
            Bundle weatherMetadata = new Bundle();
            weatherMetadata.putString(BUNDLE_TAG, TAG_WEATHER);
            Result result = super.onRunTask(weatherMetadata);
            if (result != Result.SUCCESS) {
                return result;
            }

            Bundle astronomyMetadata = new Bundle();
            astronomyMetadata.putString(BUNDLE_TAG, TAG_ASTRONOMY);
            return super.onRunTask(astronomyMetadata);
        } else if (TAG_WEATHER.equals(extras.getString(BUNDLE_TAG))
                && hasRunRecently(this)) {
            return Result.SUCCESS;
        }

        return super.onRunTask(extras);
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
        weather.restore(this);
        if (!weather.fetch(this, json)) {
            throw new JSONException("Failed to parse data");
        }
        weather.save(this);
        broadcast(ACTION_DATA_CHANGED);
    }
}
