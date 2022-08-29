package com.xlythe.service.weather;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

/**
 * Parses OpenWeather for the current weather at the user's lat/lng.
 *
 * Supports {@link #getCondition()}, {@link #getCelsius()}, {@link #getFahrenheit()},
 * {@link #getWindKph()}, {@link #getWindMph()}, {@link #getSunrise()}, and {@link #getSunset()}.
 */
public class OpenWeather extends Weather {
    public static final String TAG = OpenWeather.class.getSimpleName();

    public static final Parcelable.Creator<Weather> CREATOR = new Parcelable.Creator<Weather>() {
        public Weather createFromParcel(Parcel in) {
            return new OpenWeather(in);
        }

        public Weather[] newArray(int size) {
            return new Weather[size];
        }
    };

    public OpenWeather() {
        super();
    }

    private OpenWeather(Parcel in) {
        super(in);
    }

    public OpenWeather(Context context) {
        super(context, OpenWeatherService.ACTION_DATA_CHANGED);
    }

    @WorkerThread
    @Override
    public boolean fetch(Context context, Object... args) {
        String json = (String) args[0];

        try {
            // Parse the json
            JSONObject root = new JSONObject(json);

            if (DEBUG)
                Log.d(TAG, "OpenWeather json: " + root);

            // Start persisting values
            setCondition(OpenWeather.toCondition(root.getJSONArray("weather").getJSONObject(0).getString("main")));
            setCelsius(OpenWeather.toCelsius(root.getJSONObject("main").getDouble("temp")));
            setWindKph(toKilometers(root.getJSONObject("wind").getDouble("speed")));

            if (DEBUG)
                Log.d(TAG, "Weather set to " + getCondition() + ", " + getFahrenheit() + "F");

            setSunrise(new Time(getHour(1000 * root.getJSONObject("sys").getLong("sunrise")),
                    getMinute(1000 * root.getJSONObject("sys").getLong("sunrise"))));
            if (DEBUG)
                Log.d(TAG, "Sunrise set to " + getSunrise());

            setSunset(new Time(getHour(1000 * root.getJSONObject("sys").getLong("sunset")),
                    getMinute(1000 * root.getJSONObject("sys").getLong("sunset"))));
            if (DEBUG)
                Log.d(TAG, "Sunset set to " + getSunset());
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
        if (condition.contains("rain") || condition.contains("storm")) {
            return Condition.RAIN;
        }
        if (condition.contains("cloud")) {
            return Condition.CLOUDY;
        }
        return Condition.SUNNY;
    }

    private static float toCelsius(double kelvin) {
        return (float) (kelvin - 273);
    }

    private static int toKilometers(double kilometers) {
        return (int) kilometers;
    }

    private static int getHour(long timeInMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    private static int getMinute(long timeInMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        return calendar.get(Calendar.MINUTE);
    }
}
