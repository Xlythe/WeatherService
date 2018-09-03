package com.xlythe.service.weather;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.snapshot.WeatherResponse;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.ExecutionException;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import static com.google.android.gms.awareness.state.Weather.CELSIUS;
import static com.google.android.gms.awareness.state.Weather.CONDITION_CLOUDY;
import static com.google.android.gms.awareness.state.Weather.CONDITION_RAINY;
import static com.google.android.gms.awareness.state.Weather.CONDITION_SNOWY;
import static com.google.android.gms.awareness.state.Weather.CONDITION_STORMY;

/**
 * Supports {@link #getCondition()}, {@link #getCelsius()}, {@link #getFahrenheit()},
 * {@link #getSunrise()}, and {@link #getSunset()}.
 */
public class AwarenessWeather extends Weather {
    private static final String TAG = AwarenessWeather.class.getSimpleName();

    public static final Parcelable.Creator<Weather> CREATOR = new Parcelable.Creator<Weather>() {
        public Weather createFromParcel(Parcel in) {
            return new AwarenessWeather(in);
        }

        public Weather[] newArray(int size) {
            return new Weather[size];
        }
    };

    public AwarenessWeather() {
        super();
    }

    private AwarenessWeather(Parcel in) {
        super(in);
    }

    public AwarenessWeather(Context context) {
        super(context, AwarenessWeatherService.ACTION_DATA_CHANGED);
    }

    @WorkerThread
    @Override
    public boolean fetch(Context context, Object... args) {
        com.google.android.gms.awareness.state.Weather weather = getWeather(context);
        if (weather == null) {
            if (DEBUG) Log.d(TAG, "No weather found");
            return false;
        }

        setCelsius(weather.getTemperature(CELSIUS));
        setCondition(Condition.SUNNY);
        for (int condition : weather.getConditions()) {
            switch (condition) {
                case CONDITION_RAINY:
                case CONDITION_STORMY:
                    setCondition(Condition.RAIN);
                    break;
                case CONDITION_SNOWY:
                    setCondition(Condition.SNOW);
                    break;
                case CONDITION_CLOUDY:
                    setCondition(Condition.CLOUDY);
                    break;
            }
        }

        if (DEBUG)
            Log.d(TAG, "Weather set to " + getCondition() + ", " + getFahrenheit() + "F");

        return true;
    }

    @WorkerThread
    @Nullable
    @SuppressWarnings({"MissingPermission"})
    private com.google.android.gms.awareness.state.Weather getWeather(Context context) {
        if (!PermissionUtils.hasPermissions(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (DEBUG)
                Log.d(TAG, "Failed to get Awareness Weather due to lack of location permissions");
            return null;
        }

        try {
            if (DEBUG)
                Log.d(TAG, "Requesting weather from Awareness");
            return Tasks.await(Awareness.getSnapshotClient(context).getWeather()).getWeather();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (DEBUG)
                Log.d(TAG, "Failed to get weather from Awareness", e);
            return null;
        } catch (ExecutionException e) {
            if (DEBUG)
                Log.d(TAG, "Failed to get weather from Awareness", e);
            return null;
        }
    }
}
