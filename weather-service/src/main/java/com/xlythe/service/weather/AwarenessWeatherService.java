package com.xlythe.service.weather;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

/**
 * A service that queries the GoogleApiClient for the latest weather information
 */
public class AwarenessWeatherService extends GcmTaskService {
    private static final String TAG = AwarenessWeatherService.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final String ACTION_DATA_CHANGED = "com.xlythe.service.weather.AWARENESS_DATA_CHANGED";
    public static final String ACTION_RUN_MANUALLY = "com.xlythe.service.ACTION_RUN_MANUALLY";

    private static final long FREQUENCY_WEATHER = 30 * 60; // 30min in seconds
    private static final long FLEX = 15 * 60; // 15min in seconds

    private static final String BUNDLE_SCHEDULED = "scheduled";
    private static final String BUNDLE_SCHEDULE_TIME = "schedule_time";
    private static final HandlerThread sBackgroundThread = new HandlerThread("ServiceBackgroundThread");

    static {
        sBackgroundThread.start();
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
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
        gcmNetworkManager.cancelTask(AwarenessWeatherService.class.getSimpleName(), AwarenessWeatherService.class);
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
        AwarenessWeather weather = new AwarenessWeather();
        weather.restore(context);
        return weather.getLastUpdate() > System.currentTimeMillis() - multiplier * FREQUENCY_WEATHER;
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(AwarenessWeatherService.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();
        if (DEBUG) Log.d(TAG, "onStartCommand() action=" + action);
        if (ACTION_RUN_MANUALLY.equals(action) && isScheduled(this)) {
            new Handler(sBackgroundThread.getLooper()).post(new Runnable() {
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
    public int onRunTask(final TaskParams params) {
        if (hasRunRecently(this, 1)) {
            return GcmNetworkManager.RESULT_SUCCESS;
        }

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
