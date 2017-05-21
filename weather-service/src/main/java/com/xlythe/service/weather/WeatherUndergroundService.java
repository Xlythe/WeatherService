package com.xlythe.service.weather;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private static final long FREQUENCY_WEATHER = 2 * 60 * 60; // 2 hours in seconds
    private static final long FLEX = 30 * 60; // 30min in seconds

    private static final String BUNDLE_SCHEDULED = "scheduled";
    private static final String BUNDLE_SCHEDULE_TIME = "schedule_time";
    private static final String BUNDLE_API_KEY = "api_key";

    private static final String URL = "http://api.wunderground.com/api/%s/geolookup/conditions/q/%s,%s.json"; // apiKey, latitude, longitude

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
        PeriodicTask task = new PeriodicTask.Builder()
                .setService(WeatherUndergroundService.class)
                .setTag(WeatherUndergroundService.class.getSimpleName())
                .setPeriod(FREQUENCY_WEATHER)
                .setFlex(FLEX)
                .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
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
        context = context.getApplicationContext();
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);
        gcmNetworkManager.cancelTask(WeatherUndergroundService.class.getSimpleName(), WeatherUndergroundService.class);
        getSharedPreferences(context).edit().putBoolean(BUNDLE_SCHEDULED, false).apply();
    }

    public static boolean isScheduled(Context context) {
        context = context.getApplicationContext();
        boolean isScheduled = getSharedPreferences(context).getBoolean(BUNDLE_SCHEDULED, false);

        int multiplier = 2;
        if (isScheduled && !hasRunRecently(context, multiplier)) {
            // We are scheduled, but something has happened. We haven't run in the past 2 times.
            // It's possible that we were unscheduled somehow (eg. app was reinstalled).
            long lastSchedule = getSharedPreferences(context).getLong(BUNDLE_SCHEDULE_TIME, 0);
            if (lastSchedule  < System.currentTimeMillis() - multiplier * FREQUENCY_WEATHER) {
                // If we haven't rescheduled ourselves recently, then say we aren't scheduled.
                return false;
            }
        }

        return isScheduled;
    }

    private static boolean hasRunRecently(Context context, int multiplier) {
        Weather weather = new WeatherUnderground();
        weather.restore(context);
        return weather.getLastUpdate() > System.currentTimeMillis() - multiplier * FREQUENCY_WEATHER;
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(WeatherUndergroundService.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    private String getApiKey() {
        return WeatherUndergroundService.getApiKey(this);
    }

    public static String getApiKey(Context context) {
        return getSharedPreferences(context).getString(BUNDLE_API_KEY, null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();
        if (DEBUG) Log.d(TAG, "onStartCommand() action=" + action);
        if (ACTION_RUN_MANUALLY.equals(action) && isScheduled(this)) {
            post(new Runnable() {
                @Override
                public void run() {
                    onRunTask(new TaskParams(action));
                    stopSelf();
                }
            });
            return START_NOT_STICKY;
        } else {
            return super.onStartCommand(intent, flags, startId);
        }
    }

    @Override
    public int onRunTask(TaskParams params) {
        if (hasRunRecently(this, 1)) {
            return GcmNetworkManager.RESULT_SUCCESS;
        }
        return super.onRunTask(params);
    }

    @Override
    protected String createUrl(double latitude, double longitude) {
        return new Builder()
                .url(String.format(URL, getApiKey(), latitude, longitude))
                .build();
    }

    @Override
    protected void parse(String json) throws JSONException {
        WeatherUnderground weather = new WeatherUnderground();
        if (!weather.fetch(this, json)) {
            throw new JSONException("Failed to parse data");
        }
        weather.save(this);
        broadcast();
    }

    private void broadcast() {
        Intent intent = new Intent(ACTION_DATA_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @SuppressWarnings("MissingPermission")
    @Override
    public void onInitializeTasks() {
        if (WeatherUndergroundService.isScheduled(this)) {
            WeatherUndergroundService.schedule(this, getApiKey());
        }
    }
}
