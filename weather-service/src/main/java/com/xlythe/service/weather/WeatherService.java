package com.xlythe.service.weather;

import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

public abstract class WeatherService extends GcmTaskService {
    private static final String TAG = WeatherService.class.getSimpleName();
    static final boolean DEBUG = false;

    public static final String ACTION_RUN_MANUALLY = "com.xlythe.service.ACTION_RUN_MANUALLY";
    private static final HandlerThread sBackgroundThread = new HandlerThread("ServiceBackgroundThread");

    static {
        sBackgroundThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();
        if (DEBUG) Log.d(TAG, "onStartCommand() action=" + action);
        if (ACTION_RUN_MANUALLY.equals(action)) {
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

    static void post(Runnable runnable) {
        new Handler(sBackgroundThread.getLooper()).post(runnable);
    }

    protected void broadcast(String action) {
        Intent intent = new Intent(action);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public void onInitializeTasks() {
        if (isScheduled()) {
            Log.v(TAG, "Rescheduling " + getClass().getSimpleName());
            schedule(getApiKey());
        }
    }

    protected abstract boolean isScheduled();

    protected abstract void schedule(String apiKey);

    protected abstract void cancel();

    protected abstract String getApiKey();
}
