package com.xlythe.service.weather;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.RequiresPermission;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.AwarenessStatusCodes;
import com.google.android.gms.awareness.fence.FenceState;
import com.google.android.gms.awareness.fence.FenceUpdateRequest;
import com.google.android.gms.awareness.fence.TimeFence;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

import java.util.Calendar;

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

    private static final long FREQUENCY_WEATHER = 15 * 60; // 15min in seconds
    private static final long FLEX = 5 * 60; // 5min in seconds

    private static final String BUNDLE_SCHEDULED = "scheduled";
    private static final String BUNDLE_SCHEDULE_TIME = "schedule_time";
    private static final String BUNDLE_FREQUENCY = "frequency";
    private static final String BUNDLE_TIME_OFFSET = "time_offset";

    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
    })
    public static void schedule(final Context context) {
        GcmNetworkManager gcmNetworkManager = GcmNetworkManager.getInstance(context);
        PeriodicTask task = new PeriodicTask.Builder()
                .setService(AwarenessWeatherService.class)
                .setTag(TAG)
                .setPeriod(getFrequency(context))
                .setFlex(FLEX)
                .setPersisted(true)
                .setUpdateCurrent(true)
                .build();
        gcmNetworkManager.schedule(task);

        post(new Runnable() {
            @Override
            public void run() {
                registerForSunriseSunset(context);
            }
        });

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

    public static void setFrequency(Context context, long frequencyInMillis) {
        getSharedPreferences(context).edit()
                .putBoolean(BUNDLE_SCHEDULED, true)
                .putLong(BUNDLE_FREQUENCY, frequencyInMillis)
                .apply();
    }

    private static long getFrequency(Context context) {
        return getSharedPreferences(context).getLong(BUNDLE_FREQUENCY, FREQUENCY_WEATHER * 1000) / 1000;
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
    public int onStartCommand(Intent intent, int flags, int startId) {
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
                weather.restore(this);
                weather.setSunrise(new Weather.Time(hour, minute));
                weather.save(this);

                if (DEBUG) Log.d(TAG, "Sunrise set to " + weather.getSunrise());

                broadcast(ACTION_DATA_CHANGED);
            } else {
                if (DEBUG) Log.d(TAG, "Sunrise state set to " + toString(fenceState.getCurrentState()));
            }

            stopSelf();
            return START_NOT_STICKY;
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
                weather.restore(this);
                weather.setSunset(new Weather.Time(hour, minute));
                weather.save(this);

                if (DEBUG) Log.d(TAG, "Sunset set to " + weather.getSunset());

                broadcast(ACTION_DATA_CHANGED);
            } else {
                if (DEBUG) Log.d(TAG, "Sunset state set to " + toString(fenceState.getCurrentState()));
            }

            stopSelf();
            return START_NOT_STICKY;
        } else {
            return super.onStartCommand(intent, flags, startId);
        }
    }

    @Override
    public int onRunTask(final TaskParams params) {
        if (hasRunRecently(this)
                && !ACTION_RUN_MANUALLY.equals(params.getTag())) {
            if (DEBUG) Log.d(TAG, "Ignoring onRunTask; already ran recently");
            return GcmNetworkManager.RESULT_SUCCESS;
        }

        AwarenessWeather weather = new AwarenessWeather();
        weather.restore(this);
        if (!weather.fetch(this)) {
            return GcmNetworkManager.RESULT_RESCHEDULE;
        }
        weather.save(this);

        broadcast(ACTION_DATA_CHANGED);

        return GcmNetworkManager.RESULT_SUCCESS;
    }

    @WorkerThread
    private static boolean registerForSunriseSunset(Context context) {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(context.getApplicationContext())
                .addApi(Awareness.API)
                .build();
        try {
            ConnectionResult result = googleApiClient.blockingConnect();
            if (!result.isSuccess()) {
                if (DEBUG) Log.d(TAG, String.format("Failed to connect to GoogleApiClient: [%d]%s",
                        result.getErrorCode(),
                        AwarenessStatusCodes.getStatusCodeString(result.getErrorCode())));
                return false;
            }

            if (DEBUG) Log.d(TAG, "Connected to Awareness API");

            if (!registerForSunrise(context, googleApiClient)) {
                if (DEBUG) Log.d(TAG, "Failed to register for sunrise");
                return false;
            }

            if (!registerForSunset(context, googleApiClient)) {
                if (DEBUG) Log.d(TAG, "Failed to register for sunset");
                return false;
            }
        } finally {
            if (googleApiClient.isConnected()) {
                googleApiClient.disconnect();
            }
        }

        if (DEBUG) Log.d(TAG, "Successfully registered for sunrise and sunset");
        return true;
    }

    private static Intent getIntent(Context context, String action) {
        return new Intent(context, AwarenessWeatherService.class).setAction(action);
    }

    private static PendingIntent getPendingIntent(Context context, Intent intent) {
        return PendingIntent.getService(context, intent.getAction().hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @WorkerThread
    @SuppressWarnings({"MissingPermission"})
    private static boolean registerForSunrise(Context context, GoogleApiClient googleApiClient) {
        if (!PermissionUtils.hasPermissions(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            return false;
        }

        // We register ourselves for 1hr - 55min before sunrise, so that we'll be called sometime within that window.
        // When we are called, we then update the Weather with a timestamp of 1hr from now.
        return Awareness.FenceApi.updateFences(googleApiClient, new FenceUpdateRequest.Builder()
                .addFence(
                        TAG_SUNRISE,
                        TimeFence.aroundTimeInstant(TimeFence.TIME_INSTANT_SUNRISE, -SUNRISE_SUNSET_ADVANCE, -SUNRISE_SUNSET_ADVANCE + SUNRISE_SUNSET_FLEX),
                        getPendingIntent(context, getIntent(context, ACTION_SUNRISE).putExtra(BUNDLE_TIME_OFFSET, SUNRISE_SUNSET_ADVANCE)))
                .build()).await().isSuccess();
    }

    @WorkerThread
    @SuppressWarnings({"MissingPermission"})
    private static boolean registerForSunset(Context context, GoogleApiClient googleApiClient) {
        if (!PermissionUtils.hasPermissions(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            return false;
        }

        return Awareness.FenceApi.updateFences(googleApiClient, new FenceUpdateRequest.Builder()
                .addFence(
                        TAG_SUNSET,
                        TimeFence.aroundTimeInstant(TimeFence.TIME_INSTANT_SUNSET, -SUNRISE_SUNSET_ADVANCE, -SUNRISE_SUNSET_ADVANCE + SUNRISE_SUNSET_FLEX),
                getPendingIntent(context, getIntent(context, ACTION_SUNSET).putExtra(BUNDLE_TIME_OFFSET, SUNRISE_SUNSET_ADVANCE)))
                .build()).await().isSuccess();
    }

    private static String toString(@FenceState.State int fenceState) {
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
}
