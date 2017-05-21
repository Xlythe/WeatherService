package com.xlythe.service.weather;

import android.content.Context;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses wunderground for the current weather at the user's lat/lng.
 *
 * Supports {@link #getCondition()}, {@link #getCelsius()}, {@link #getFahrenheit()},
 * {@link #getSunrise()}, {@link #getSunset()}, and {@link #getMoonPhase()}.
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
            JSONObject root = new JSONObject(json);

            if (root.has("current_observation")) {
                // Parse the json
                JSONObject object = root.getJSONObject("current_observation");

                // Start persisting values
                setCondition(WeatherUnderground.toCondition(object.getString("weather")));
                setCelsius((float) object.getDouble("temp_c"));
            } else if (root.has("moon_phase")) {
                // Parse the json
                JSONObject moon_phase = root.getJSONObject("moon_phase");
                setMoonPhase(toMoonPhase(moon_phase.getInt("ageOfMoon")));

                JSONObject sunrise = moon_phase.getJSONObject("sunrise");
                setSunrise(new Time(sunrise.getInt("hour"), sunrise.getInt("minute")));

                JSONObject sunset = moon_phase.getJSONObject("sunset");
                setSunset(new Time(sunset.getInt("hour"), sunset.getInt("minute")));
            } else {
                Log.w(TAG, "Unknown JSON object " + root);
                return false;
            }

            if (DEBUG)
                Log.d(TAG, "WeatherUnderground set to " + toString());
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

    private static MoonPhase toMoonPhase(int ageOfMoon) {
        // Age of moon is between 0 and 29
        if (ageOfMoon == -1) {
            return MoonPhase.FULL_MOON;
        } else if (ageOfMoon < 3) {
            return MoonPhase.NEW_MOON;
        } else if (ageOfMoon < 7) {
            return MoonPhase.WAXING_CRESCENT;
        } else if (ageOfMoon < 10) {
            return MoonPhase.FIRST_QUARTER;
        } else if (ageOfMoon < 13) {
            return MoonPhase.WAXING_GIBBOUS;
        } else if (ageOfMoon < 19) {
            return MoonPhase.FULL_MOON;
        } else if (ageOfMoon < 22) {
            return MoonPhase.WANING_GIBBOUS;
        } else if (ageOfMoon < 24) {
            return MoonPhase.THIRD_QUARTER;
        } else if (ageOfMoon < 28) {
            return MoonPhase.WANING_CRESCENT;
        } else {
            return MoonPhase.NEW_MOON;
        }
    }
}
