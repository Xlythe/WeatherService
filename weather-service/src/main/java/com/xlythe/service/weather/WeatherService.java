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
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.futures.SettableFuture;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.UUID;

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

    @NonNull
    @UiThread
    @Override
    @SuppressLint("RestrictedApi")
    public ListenableFuture<ListenableWorker.Result> startWork() {
        SettableFuture<ListenableWorker.Result> future = SettableFuture.create();
        post(() -> {
            switch (onRunTask(toBundle(getInputData()))) {
                case SUCCESS:
                    future.set(ListenableWorker.Result.success());
                    break;
                case FAILURE:
                    future.set(ListenableWorker.Result.failure());
                    break;
                case RESCHEDULE:
                    future.set(ListenableWorker.Result.retry());
                    break;
            }
        });
        return future;
    }

    protected static Bundle toBundle(Data data) {
        Bundle bundle = new Bundle();
        for (String key : data.getKeyValueMap().keySet()) {
            if (data.hasKeyWithValueOfType(key, Boolean.TYPE)) {
                bundle.putBoolean(key, data.getBoolean(key, false));
            } else if (data.hasKeyWithValueOfType(key, Byte.TYPE)) {
                bundle.putByte(key, data.getByte(key, (byte) 0));
            } else if (data.hasKeyWithValueOfType(key, Byte[].class)) {
                bundle.putByteArray(key, data.getByteArray(key));
            } else if (data.hasKeyWithValueOfType(key, Double.TYPE)) {
                bundle.putDouble(key, data.getDouble(key, 0D));
            } else if (data.hasKeyWithValueOfType(key, Double[].class)) {
                bundle.putDoubleArray(key, data.getDoubleArray(key));
            } else if (data.hasKeyWithValueOfType(key, Float.TYPE)) {
                bundle.putFloat(key, data.getFloat(key, 0F));
            } else if (data.hasKeyWithValueOfType(key, Float[].class)) {
                bundle.putFloatArray(key, data.getFloatArray(key));
            } else if (data.hasKeyWithValueOfType(key, Integer.TYPE)) {
                bundle.putInt(key, data.getInt(key, 0));
            } else if (data.hasKeyWithValueOfType(key, Integer[].class)) {
                bundle.putIntArray(key, data.getIntArray(key));
            } else if (data.hasKeyWithValueOfType(key, Long.TYPE)) {
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
        return new Data.Builder().build();
    }

    @UiThread
    @Override
    public void onStopped() {
        // Ignored
    }

    protected static void runImmediately(Context context, Class<? extends WeatherService> clazz, @Nullable Bundle extras) {
        if (DEBUG) {
            Log.d(clazz.getSimpleName(), "Running " + clazz.getSimpleName() + " immediately");
        }

        post(() -> {
            try {
                Constructor<? extends WeatherService> constructor = clazz.getConstructor(Context.class, WorkerParameters.class);
                @SuppressLint("RestrictedApi") WeatherService service = constructor.newInstance(context, new WorkerParameters(
                        UUID.randomUUID(),
                        Data.EMPTY,
                        new HashSet<>(),
                        null,
                        0,
                        0,
                        ContextCompat.getMainExecutor(context),
                        null,
                        null,
                        null,
                        null));

                if (DEBUG) {
                    Log.d(clazz.getSimpleName(), "Now executing " + clazz.getSimpleName() + ".startWork");
                }
                service.onRunTask(extras);
            } catch (Exception e) {
                if (DEBUG) {
                    Log.d(clazz.getSimpleName(), "Failed to run immediately", e);
                }
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
        broadcast(getContext(), action);
    }

    @WorkerThread
    protected abstract Result onRunTask(@Nullable Bundle extras);

    protected abstract boolean isScheduled();

    protected abstract void schedule(String apiKey);

    protected abstract void cancel();

    protected abstract String getApiKey();
}
