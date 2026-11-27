package com.xlythe.service.weather;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.futures.SettableFuture;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class WeatherService extends ListenableWorker {
    static final boolean DEBUG = Weather.DEBUG;

    private static final HandlerThread sBackgroundThread = new HandlerThread("ServiceBackgroundThread");

    protected enum Result {
        SUCCESS, RESCHEDULE, FAILURE;
    }

    static {
        sBackgroundThread.start();
    }

    public WeatherService(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
    }

    protected Context getContext() {
        return getApplicationContext();
    }

    private static final String MANUAL_REFRESH = "com.xlythe.service.weather.MANUAL_REFRESH";

    static boolean isFresh(long updatedAt, long now, long intervalSeconds, long flexSeconds) {
        long age = now - updatedAt;
        return updatedAt > 0 && age >= 0 && age < TimeUnit.SECONDS.toMillis(Math.max(0, intervalSeconds - flexSeconds));
    }

    @NonNull
    @UiThread
    @Override
    @SuppressLint("RestrictedApi")
    public ListenableFuture<ListenableWorker.Result> startWork() {
        SettableFuture<ListenableWorker.Result> future = SettableFuture.create();
        post(() -> {
            if (isStopped()) {
                future.cancel(false);
                return;
            }
            try {
                Bundle extras = getInputData().getBoolean(MANUAL_REFRESH, false) ? null : toBundle(getInputData());
                switch (onRunTask(extras)) {
                    case SUCCESS: future.set(ListenableWorker.Result.success()); break;
                    case FAILURE: future.set(ListenableWorker.Result.failure()); break;
                    case RESCHEDULE: future.set(ListenableWorker.Result.retry()); break;
                }
            } catch (RuntimeException error) {
                future.setException(error);
            }
        });
        return future;
    }

    protected static Bundle toBundle(Data data) {
        Bundle bundle = new Bundle();
        for (String key : data.getKeyValueMap().keySet()) {
            if (data.hasKeyWithValueOfType(key, Boolean.class)) {
                bundle.putBoolean(key, data.getBoolean(key, false));
            } else if (data.hasKeyWithValueOfType(key, Boolean[].class)) {
                bundle.putBooleanArray(key, data.getBooleanArray(key));
            } else if (data.hasKeyWithValueOfType(key, Byte.class)) {
                bundle.putByte(key, data.getByte(key, (byte) 0));
            } else if (data.hasKeyWithValueOfType(key, Byte[].class)) {
                bundle.putByteArray(key, data.getByteArray(key));
            } else if (data.hasKeyWithValueOfType(key, Double.class)) {
                bundle.putDouble(key, data.getDouble(key, 0D));
            } else if (data.hasKeyWithValueOfType(key, Double[].class)) {
                bundle.putDoubleArray(key, data.getDoubleArray(key));
            } else if (data.hasKeyWithValueOfType(key, Float.class)) {
                bundle.putFloat(key, data.getFloat(key, 0F));
            } else if (data.hasKeyWithValueOfType(key, Float[].class)) {
                bundle.putFloatArray(key, data.getFloatArray(key));
            } else if (data.hasKeyWithValueOfType(key, Integer.class)) {
                bundle.putInt(key, data.getInt(key, 0));
            } else if (data.hasKeyWithValueOfType(key, Integer[].class)) {
                bundle.putIntArray(key, data.getIntArray(key));
            } else if (data.hasKeyWithValueOfType(key, Long.class)) {
                bundle.putLong(key, data.getLong(key, 0L));
            } else if (data.hasKeyWithValueOfType(key, Long[].class)) {
                bundle.putLongArray(key, data.getLongArray(key));
            } else if (data.hasKeyWithValueOfType(key, String.class)) {
                bundle.putString(key, data.getString(key));
            } else if (data.hasKeyWithValueOfType(key, String[].class)) {
                bundle.putStringArray(key, data.getStringArray(key));
            }
        }
        return bundle;
    }

    protected static Data toData(Bundle bundle) {
        Map<String, Object> values = new HashMap<>();
        for (String key : bundle.keySet()) values.put(key, bundle.get(key));
        return new Data.Builder().putAll(values).build();
    }

    @UiThread
    @Override
    public void onStopped() {
        // Ignored
    }

    protected static void runImmediately(Context context, Class<? extends WeatherService> clazz, @Nullable Bundle extras) {
        Data input = extras == null ? new Data.Builder().putBoolean(MANUAL_REFRESH, true).build() : toData(extras);
        OneTimeWorkRequest.Builder request = new OneTimeWorkRequest.Builder(clazz).setInputData(input);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            request.setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        }
        WorkManager.getInstance(context).enqueue(request.build());
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
        broadcast(getContext(), action);
    }

    @WorkerThread
    protected abstract Result onRunTask(@Nullable Bundle extras);

    protected abstract boolean isScheduled();

    protected abstract void schedule(String apiKey);

    protected abstract void cancel();

    protected abstract String getApiKey();
}
