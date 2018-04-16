package com.xlythe.service.weather;

import android.Manifest;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.snapshot.WeatherResponse;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.ExecutionException;

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
            return null;
        }

        try {
            WeatherResponse weatherResponse = Tasks.await(Awareness.getSnapshotClient(context).getWeather());
            return weatherResponse.getWeather();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }
}
