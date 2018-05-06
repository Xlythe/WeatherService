package com.xlythe.service.weather;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.Trigger;
import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.fence.FenceState;
import com.google.android.gms.awareness.fence.FenceUpdateRequest;
import com.google.android.gms.awareness.fence.TimeFence;
import com.google.android.gms.tasks.Tasks;

import java.util.Calendar;
import java.util.concurrent.ExecutionException;

/**
 * A service that queries the GoogleApiClient for the latest weather information
 */
public class AwarenessWeatherService extends WeatherService {
    private static final String TAG = AwarenessWeatherService.class.getSimpleName();
    private static final String TAG_SUNRISE = TAG + "_SUNRISE";
    private static final String TAG_SUNSET = TAG + "_SUNSET";

    /**
     * This is how much flex time we have to get sunrise/sunset. We will be notified some time
     * within this window. Too short and we won't be notified. Too long and we may be off from the
     * actual sunrise/sunset time.
     */
    private static final long SUNRISE_SUNSET_FLEX = 15 * 60 * 1000; /* 15min */

    /**
     * This is how soon before today's sunrise/sunset we ask to be notified
     */
    private static final long SUNRISE_SUNSET_ADVANCE = 12 * 60 * 60 * 1000; /* 12hr */

    public static final String ACTION_DATA_CHANGED = "com.xlythe.service.weather.AWARENESS_DATA_CHANGED";
    private static final String ACTION_SUNRISE = "com.xlythe.service.weather.AWARENESS_SUNRISE";
    private static final String ACTION_SUNSET = "com.xlythe.service.weather.AWARENESS_SUNSET";

    private static final int FREQUENCY_WEATHER = 15 * 60; // 15min in seconds
    private static final int FLEX = 5 * 60; // 5min in seconds

    private static final String BUNDLE_SCHEDULED = "scheduled";
    private static final String BUNDLE_SCHEDULE_TIME = "schedule_time";
    private static final String BUNDLE_FREQUENCY = "frequency";
    private static final String BUNDLE_TIME_OFFSET = "time_offset";

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION
    })
    public static void runImmediately(Context context) {
        runImmediately(context, AwarenessWeatherService.class, null);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    public static void schedule(Context context) {
        if (DEBUG) Log.d(TAG, "Scheduling Awareness api");
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context));
        dispatcher.mustSchedule(dispatcher.newJobBuilder()
                .setService(AwarenessWeatherService.class)
                .setTag(TAG)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(getFrequency(context), getFrequency(context) + FLEX))
                .setLifetime(Lifetime.FOREVER)
                .setReplaceCurrent(true)
                .build());

        post(() -> registerForSunriseSunset(context));

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
        Weather weather = new AwarenessWeather();
        weather.restore(context);
        return weather.getLastUpdate() > System.currentTimeMillis() - getFrequency(context) + FLEX;
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
    public Result onRunTask(@Nullable Bundle extras) {
        // Extras are null when run manually.
        if (extras != null && hasRunRecently(this)) {
            if (DEBUG) Log.d(TAG, "Ignoring onRunTask; already ran recently");
            return Result.SUCCESS;
        }

        if (DEBUG)
            Log.d(TAG, "Running AwarenessWeatherService task");

        AwarenessWeather weather = new AwarenessWeather();
        weather.restore(this);
        if (DEBUG)
            Log.d(TAG, "Successfully restored our saved state");
        if (!weather.fetch(this)) {
            if (DEBUG)
                Log.d(TAG, "Failed to fetch. Rescheduling.");
            return Result.RESCHEDULE;
        }
        weather.save(this);
        if (DEBUG)
            Log.d(TAG, "Successfully saved our new state");

        broadcast(ACTION_DATA_CHANGED);

        return Result.SUCCESS;
    }

    @WorkerThread
    private static boolean registerForSunriseSunset(Context context) {
        if (!registerForSunrise(context)) {
            if (DEBUG) Log.d(TAG, "Failed to register for sunrise");
            return false;
        }

        if (!registerForSunset(context)) {
            if (DEBUG) Log.d(TAG, "Failed to register for sunset");
            return false;
        }

        if (DEBUG) Log.d(TAG, "Successfully registered for sunrise and sunset");
        return true;
    }

    @WorkerThread
    @SuppressWarnings({"MissingPermission"})
    private static boolean registerForSunrise(Context context) {
        if (!PermissionUtils.hasPermissions(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (DEBUG)
                Log.d(TAG, "Failed to register for sunrise due to lack of location permissions");
            return false;
        }

        // We register ourselves for 1hr - 55min before sunrise, so that we'll be called sometime within that window.
        // When we are called, we then update the Weather with a timestamp of 1hr from now.
        try {
            Tasks.await(Awareness.getFenceClient(context).updateFences(new FenceUpdateRequest.Builder()
                    .addFence(
                            TAG_SUNRISE,
                            TimeFence.aroundTimeInstant(TimeFence.TIME_INSTANT_SUNRISE, -SUNRISE_SUNSET_ADVANCE, -SUNRISE_SUNSET_ADVANCE + SUNRISE_SUNSET_FLEX),
                            AwarenessBroadcastReceiver.getPendingIntent(context, AwarenessBroadcastReceiver.getIntent(context, ACTION_SUNRISE).putExtra(BUNDLE_TIME_OFFSET, SUNRISE_SUNSET_ADVANCE)))
                    .build()));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (DEBUG)
                Log.d(TAG, "Failed to register with Awareness", e);
            return false;
        } catch (ExecutionException e) {
            if (DEBUG)
                Log.d(TAG, "Failed to register with Awareness", e);
            return false;
        }
    }

    @WorkerThread
    @SuppressWarnings({"MissingPermission"})
    private static boolean registerForSunset(Context context) {
        if (!PermissionUtils.hasPermissions(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (DEBUG)
                Log.d(TAG, "Failed to register for sunset due to lack of location permissions");
            return false;
        }

        // We register ourselves for 1hr - 55min before sunset, so that we'll be called sometime within that window.
        // When we are called, we then update the Weather with a timestamp of 1hr from now.
        try {
            Tasks.await(Awareness.getFenceClient(context).updateFences(new FenceUpdateRequest.Builder()
                    .addFence(
                            TAG_SUNSET,
                            TimeFence.aroundTimeInstant(TimeFence.TIME_INSTANT_SUNSET, -SUNRISE_SUNSET_ADVANCE, -SUNRISE_SUNSET_ADVANCE + SUNRISE_SUNSET_FLEX),
                            AwarenessBroadcastReceiver.getPendingIntent(context, AwarenessBroadcastReceiver.getIntent(context, ACTION_SUNSET).putExtra(BUNDLE_TIME_OFFSET, SUNRISE_SUNSET_ADVANCE)))
                    .build()));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (DEBUG)
                Log.d(TAG, "Failed to register with Awareness", e);
            return false;
        } catch (ExecutionException e) {
            if (DEBUG)
                Log.d(TAG, "Failed to register with Awareness", e);
            return false;
        }
    }

    public static final class AwarenessBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent == null ? null : intent.getAction();
            if (ACTION_SUNRISE.equals(action)) {
                FenceState fenceState = FenceState.extract(intent);
                if (fenceState.getCurrentState() == FenceState.TRUE) {
                    long timeOffset = intent.getExtras().getLong(BUNDLE_TIME_OFFSET);
                    if (DEBUG)
                        Log.d(TAG, String.format("Sunrise occurring in %d milliseconds", timeOffset));

                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(System.currentTimeMillis() + timeOffset);
                    int hour = calendar.get(Calendar.HOUR_OF_DAY);
                    int minute = calendar.get(Calendar.MINUTE);

                    AwarenessWeather weather = new AwarenessWeather();
                    weather.restore(context);
                    weather.setSunrise(new Weather.Time(hour, minute));
                    weather.save(context);

                    if (DEBUG) Log.d(TAG, "Sunrise set to " + weather.getSunrise());

                    broadcast(context, ACTION_DATA_CHANGED);
                } else {
                    if (DEBUG)
                        Log.d(TAG, "Sunrise state set to " + toString(fenceState.getCurrentState()));
                }
            } else if (ACTION_SUNSET.equals(action)) {
                FenceState fenceState = FenceState.extract(intent);
                if (fenceState.getCurrentState() == FenceState.TRUE) {
                    long timeOffset = intent.getExtras().getLong(BUNDLE_TIME_OFFSET);
                    if (DEBUG)
                        Log.d(TAG, String.format("Sunset occurring in %d milliseconds", timeOffset));

                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(System.currentTimeMillis() + timeOffset);
                    int hour = calendar.get(Calendar.HOUR_OF_DAY);
                    int minute = calendar.get(Calendar.MINUTE);

                    AwarenessWeather weather = new AwarenessWeather();
                    weather.restore(context);
                    weather.setSunset(new Weather.Time(hour, minute));
                    weather.save(context);

                    if (DEBUG) Log.d(TAG, "Sunset set to " + weather.getSunset());

                    broadcast(context, ACTION_DATA_CHANGED);
                } else {
                    if (DEBUG)
                        Log.d(TAG, "Sunset state set to " + toString(fenceState.getCurrentState()));
                }
            }
        }

        private static String toString(int fenceState) {
            switch (fenceState) {
                case FenceState.TRUE:
                    return "TRUE";
                case FenceState.FALSE:
                    return "FALSE";
                case FenceState.UNKNOWN:
                default:
                    return "UNKNOWN";
            }
        }

        private static Intent getIntent(Context context, String action) {
            return new Intent(context, AwarenessWeatherService.class).setAction(action);
        }

        private static PendingIntent getPendingIntent(Context context, Intent intent) {
            return PendingIntent.getBroadcast(context, intent.getAction().hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }
}
