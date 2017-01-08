package com.xlythe.service.weather;

import android.content.Context;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses open weather map for the current OpenWeather at the user's lat/long
 *
 * Supports {@link #getCondition()}, {@link #getCelsius()}, and {@link #getFahrenheit()}
 */
public class WeatherUnderground extends Weather {
    public static final String TAG = WeatherUnderground.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final Creator<Weather> CREATOR = new Creator<Weather>() {
        public Weather createFromParcel(Parcel in) {
            return new WeatherUnderground(in);
        }

        public Weather[] newArray(int size) {
            return new Weather[size];
        }
    };

    public WeatherUnderground() {
        super();
    }

    private WeatherUnderground(Parcel in) {
        super(in);
    }

    @WorkerThread
    @Override
    public boolean fetch(Context context, Object... args) {
        String json = (String) args[0];

        try {
            // Parse the json
            JSONObject object = new JSONObject(json).getJSONObject("current_observation");

            // Start persisting values
            setCondition(WeatherUnderground.toCondition(object.getString("weather")));
            setCelsius((float) object.getDouble("temp_c"));

            if (DEBUG)
                Log.d(TAG, "WeatherUnderground set to " + getCondition() + ", " + getFahrenheit() + "F");
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static Condition toCondition(@NonNull String condition) {
        condition = condition.toLowerCase();
        if (condition.contains("snow")) {
            return Condition.SNOW;
        }
        if (condition.contains("rain") || condition.contains("storm") || condition.contains("thunder")) {
            return Condition.RAIN;
        }
        if (condition.contains("cloud") || condition.contains("overcast") || condition.contains("fog")) {
            return Condition.CLOUDY;
        }
        return Condition.SUNNY;
    }
}
