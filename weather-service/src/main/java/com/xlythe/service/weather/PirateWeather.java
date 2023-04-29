package com.xlythe.service.weather;

import android.content.Context;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses Pirate Weather for the current weather at the user's lat/lng.
 *
 * Supports {@link #getCondition()}, {@link #getCelsius()}, {@link #getFahrenheit()},
 * {@link #getSunrise()}, {@link #getSunset()}, and {@link #getMoonPhase()}.
 */
public class PirateWeather extends Weather {
    public static final String TAG = PirateWeather.class.getSimpleName();

    public static final Creator<Weather> CREATOR = new Creator<Weather>() {
        public Weather createFromParcel(Parcel in) {
            return new PirateWeather(in);
        }

        public Weather[] newArray(int size) {
            return new Weather[size];
        }
    };

    public static final String EXAMPLE = "{\"latitude\":45.42,\"longitude\":-75.69,\"timezone\":\"America/Toronto\",\"offset\":-5,\"elevation\":69,\"currently\":{\"time\":1674318840,\"summary\":\"Clear\",\"icon\":\"clear-day\",\"nearestStormDistance\":0,\"nearestStormBearing\":0,\"precipIntensity\":0,\"precipProbability\":0,\"precipIntensityError\":0,\"precipType\":\"none\",\"temperature\":-4.59,\"apparentTemperature\":-7.82,\"dewPoint\":-6.21,\"humidity\":0.88,\"pressure\":1014.3,\"windSpeed\":7.204,\"windGust\":14.18,\"windBearing\":255.53,\"cloudCover\":0.14,\"uvIndex\":2.38,\"visibility\":14.7,\"ozone\":402.2},\"minutely\":{\"summary\":\"Clear\",\"icon\":\"clear\",\"data\":[{\"time\":1674318840,\"precipIntensity\":0,\"precipProbability\":0,\"precipIntensityError\":0,\"precipType\":\"none\"}]},\"hourly\":{\"summary\":\"Cloudy\",\"icon\":\"cloudy\",\"data\":[{\"time\":1674316800,\"icon\":\"partly-cloudy-day\",\"summary\":\"Partly Cloudy\",\"precipIntensity\":0.0033,\"precipProbability\":0,\"precipIntensityError\":0.0026,\"precipAccumulation\":0.0033,\"precipType\":\"snow\",\"temperature\":-5.4,\"apparentTemperature\":-8.63,\"dewPoint\":-7.02,\"humidity\":0.9,\"pressure\":1014.4,\"windSpeed\":6.88,\"windGust\":15.08,\"windBearing\":258.69,\"cloudCover\":0.49,\"uvIndex\":1.74,\"visibility\":14.8,\"ozone\":405.38}]},\"daily\":{\"summary\":\"Snow\",\"icon\":\"cloudy\",\"data\":[{\"time\":1674277200,\"icon\":\"cloudy\",\"summary\":\"Cloudy\",\"sunriseTime\":1674304502,\"sunsetTime\":1674338008,\"moonPhase\":0.9848795204636577,\"precipIntensity\":0.0179,\"precipIntensityMax\":0.0362,\"precipIntensityMaxTime\":1674356400,\"precipProbability\":0,\"precipAccumulation\":0.2861,\"precipType\":\"none\",\"temperatureHigh\":-2.59,\"temperatureHighTime\":1674331200,\"temperatureLow\":-5.4,\"temperatureLowTime\":1674316800,\"apparentTemperatureHigh\":-2.89,\"apparentTemperatureHighTime\":1674342000,\"apparentTemperatureLow\":-8.63,\"apparentTemperatureLowTime\":1674316800,\"dewPoint\":-5.6,\"humidity\":0.848,\"pressure\":1013.11,\"windSpeed\":5.92,\"windGust\":14.4,\"windGustTime\":1674320400,\"windBearing\":210.18,\"cloudCover\":0.768,\"uvIndex\":2.38,\"uvIndexTime\":1674320400,\"visibility\":15.1,\"temperatureMin\":-5.4,\"temperatureMinTime\":1674316800,\"temperatureMax\":-2.59,\"temperatureMaxTime\":1674331200,\"apparentTemperatureMin\":-8.63,\"apparentTemperatureMinTime\":1674316800,\"apparentTemperatureMax\":-2.89,\"apparentTemperatureMaxTime\":1674342000}],\"alerts\":[{\"title\":\"Wind Advisory issued January 24 at 9:25AM CST until January 24 at 6:00PM CST by NWS Corpus Christi TX\",\"regions\":[\"Live Oak\",\" Bee\",\" Goliad\",\" Victoria\",\" Jim Wells\",\" Inland Kleberg\",\" Inland Nueces\",\" Inland San Patricio\",\" Coastal Aransas\",\" Inland Refugio\",\" Inland Calhoun\",\" Coastal Kleberg\",\" Coastal Nueces\",\" Coastal San Patricio\",\" Aransas Islands\",\" Coastal Refugio\",\" Coastal Calhoun\",\" Kleberg Islands\",\" Nueces Islands\",\" Calhoun Islands\"],\"severity\":\"Moderate\",\"time\":1674573900,\"expires\":1674604800,\"description\":\"* WHAT...Southwest winds 25 to 30 mph with gusts up to 40 mph.  * WHERE...Portions of South Texas.  * WHEN...Until 6 PM CST this evening.  * IMPACTS...Gusty winds could blow around unsecured objects. Tree limbs could be blown down and a few power outages may result.\",\"uri\":\"https://api.weather.gov/alerts/urn:oid:2.49.0.1.840.0.492c55233ef16d7a98a3337298c828b0f358ea34.001.1\"}],\"flags\":{\"sources\":[\"ETOPO1\",\"gfs\",\"gefs\",\"hrrrsubh\",\"hrrr\"],\"sourceTimes\":{\"hrrr_0-18\":\"2023-01-21 14:00:00\",\"hrrr_subh\":\"2023-01-21 14:00:00\",\"hrrr_18-48\":\"2023-01-21 12:00:00\",\"gfs\":\"2023-01-21 06:00:00\",\"gefs\":\"2023-01-21 06:00:00\"},\"nearest-station\":0,\"units\":\"ca\",\"version\":\"V1.4.1\"}}}\n";

    private static final Map<Double, MoonPhase> MOON_PHASES = new HashMap<>();

    static {
        // Moon phase is between 0.0 and 1.0
        MOON_PHASES.put(0.0, MoonPhase.NEW_MOON);
        MOON_PHASES.put(0.125, MoonPhase.WAXING_CRESCENT);
        MOON_PHASES.put(0.25, MoonPhase.FIRST_QUARTER);
        MOON_PHASES.put(0.375, MoonPhase.WAXING_GIBBOUS);
        MOON_PHASES.put(0.5, MoonPhase.FULL_MOON);
        MOON_PHASES.put(0.625, MoonPhase.WANING_GIBBOUS);
        MOON_PHASES.put(0.75, MoonPhase.THIRD_QUARTER);
        MOON_PHASES.put(0.875, MoonPhase.WANING_CRESCENT);
        MOON_PHASES.put(1.0, MoonPhase.NEW_MOON);
    }

    public PirateWeather() {
        super();
    }

    private PirateWeather(Parcel in) {
        super(in);
    }

    public PirateWeather(Context context) {
        super(context, PirateWeatherService.ACTION_DATA_CHANGED);
    }

    @WorkerThread
    @Override
    public boolean fetch(Context context, Object... args) {
        String json = (String) args[0];

        try {
            JSONObject root = new JSONObject(json);

            if (DEBUG)
                Log.d(TAG, "PirateWeather json: " + root);

            if (root.has("currently")) {
                // Parse the json
                JSONObject object = root.getJSONObject("currently");

                // Start persisting values
                setCondition(PirateWeather.toCondition(object.getString("summary")));
                setCelsius((float) object.getDouble("temperature"));

                if (DEBUG)
                    Log.d(TAG, "Weather set to " + getCondition() + ", " + getFahrenheit() + "F");
            }

            if (root.has("daily")) {
                // Parse the json
                JSONObject data = root.getJSONObject("daily").getJSONArray("data").getJSONObject(0);
                setMoonPhase(toMoonPhase(data.getInt("moonPhase")));
                if (DEBUG)
                    Log.d(TAG, "Moon phase set to " + getMoonPhase());

                setSunrise(new Time(1000 * data.getLong("sunriseTime")));
                if (DEBUG)
                    Log.d(TAG, "Sunrise set to " + getSunrise());

                setSunset(new Time(1000 * data.getLong("sunsetTime")));
                if (DEBUG)
                    Log.d(TAG, "Sunset set to " + getSunset());
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse PirateWeather json", e);
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

    private static MoonPhase toMoonPhase(double moonPhase) {
        double minDistance = Double.MAX_VALUE;
        MoonPhase closestPhase = MoonPhase.NEW_MOON;
        for (Map.Entry<Double, MoonPhase> entry : MOON_PHASES.entrySet()) {
            double distanceToPhase = Math.abs(entry.getKey() - moonPhase);
            if (distanceToPhase < minDistance) {
                minDistance = distanceToPhase;
                closestPhase = entry.getValue();
            }
        }
        return closestPhase;
    }
}
