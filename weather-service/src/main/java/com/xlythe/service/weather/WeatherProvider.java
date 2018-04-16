package com.xlythe.service.weather;

import android.content.BroadcastReceiver;
import android.content.Context;

public abstract class WeatherProvider {
    private final Context context;

    protected WeatherProvider(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public abstract void runImmediately();

    public abstract void schedule();

    public abstract void cancel();

    public abstract boolean isScheduled();

    public abstract Weather getWeather();

    public abstract void registerReceiver(BroadcastReceiver broadcastReceiver);
}
