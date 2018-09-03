package com.xlythe.service.weather;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

public abstract class WeatherService extends JobService {
    static final boolean DEBUG = Weather.DEBUG;

    private static final HandlerThread sBackgroundThread = new HandlerThread("ServiceBackgroundThread");

    protected enum Result {
        SUCCESS, RESCHEDULE, FAILURE;
    }

    static {
        sBackgroundThread.start();
    }

    protected static void runImmediately(Context context, Class<? extends WeatherService> clazz, @Nullable Bundle extras) {
        if (DEBUG)
            Log.d(clazz.getSimpleName(), "Running " + clazz.getSimpleName() + " immediately");
        post(() -> {
            try {
                WeatherService service = clazz.newInstance();
                service.attachBaseContext(context);

                if (DEBUG)
                    Log.d(clazz.getSimpleName(), "Now executing " + clazz.getSimpleName() + ".onRunTask");
                service.onRunTask(extras);
            } catch (Exception e) {
                if (DEBUG)
                    Log.d(clazz.getSimpleName(), "Failed to run immediately", e);
            }
        });
    }

    protected static void post(Runnable runnable) {
        new Handler(sBackgroundThread.getLooper()).post(runnable);
    }

    protected static void broadcast(Context context, String action) {
        Intent intent = new Intent(action);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    protected void broadcast(String action) {
        broadcast(this, action);
    }

    @UiThread
    @Override
    public boolean onStartJob(final JobParameters job) {
        post(() -> {
                switch (onRunTask(job.getExtras())) {
                    case SUCCESS:
                    case FAILURE:
                        jobFinished(job, false);
                        break;
                    case RESCHEDULE:
                        jobFinished(job, true);
                        break;
                }
            });

        // Returning true signals that there is ongoing work.
        return true;
    }

    @UiThread
    @Override
    public boolean onStopJob(JobParameters job) {
        // Returning true signals that the job should be retried as soon as possible.
        return true;
    }

    @WorkerThread
    protected abstract Result onRunTask(@Nullable Bundle extras);

    protected abstract boolean isScheduled();

    protected abstract void schedule(String apiKey);

    protected abstract void cancel();

    protected abstract String getApiKey();
}
