package com.xlythe.service.weather;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

public abstract class Weather implements ParcelableUtils.RestorableParcelable {
    static final boolean DEBUG = false;

    private static final String BUNDLE_STATE = "state:";

    private static final Condition DEFAULT_CONDITION = Condition.SUNNY;
    private static final MoonPhase DEFAULT_MOON_PHASE = MoonPhase.FULL_MOON;
    private static final Time DEFAULT_SUNRISE = new Time(6, 0);
    private static final Time DEFAULT_SUNSET = new Time(18, 0);
    private static final int DEFAULT_TEMP_C = 20;
    private static final int DEFAULT_WIND_KPH = 0;

    @Nullable private static Calendar CALENDAR;

    public enum Condition {
        SNOW, RAIN, CLOUDY, SUNNY;
    }

    public enum MoonPhase {
        NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS,
        FULL_MOON, WANING_GIBBOUS, THIRD_QUARTER, WANING_CRESCENT;
    }

    public static class Time implements Parcelable {
        public static final Parcelable.Creator<Time> CREATOR = new Parcelable.Creator<Time>() {
            public Time createFromParcel(Parcel in) {
                return new Time(in);
            }

            public Time[] newArray(int size) {
                return new Time[size];
            }
        };

        private final int hour;
        private final int minute;

        public Time(int hour, int minute) {
            this.hour = hour;
            this.minute = minute;
        }

        public Time(long timeInMillis) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timeInMillis);
            this.hour = calendar.get(Calendar.HOUR_OF_DAY);
            this.minute = calendar.get(Calendar.MINUTE);
        }

        protected Time(Parcel in) {
            hour = in.readInt();
            minute = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(hour);
            out.writeInt(minute);
        }

        public int getHour() {
            return hour;
        }

        public int getMinute() {
            return minute;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (o instanceof Time) {
                Time a = (Time) o;
                return hour == a.hour && minute == a.minute;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hour + minute;
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "%02d:%02d", hour, minute);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

    private static Time sSunrise = null;
    private static Time sSunset = null;
    private static Condition sCondition = null;
    private static long sFakeTime = -1;

    public static void overrideCondition(Condition condition) {
        sCondition = condition;
    }

    public static void overrideSunrise(int hour, int minute) {
        sSunrise = new Time(hour, minute);
    }

    public static void overrideSunset(int hour, int minute) {
        sSunset = new Time(hour, minute);
    }

    public static void overrideTime(long timeInMillis) {
        sFakeTime = timeInMillis;
    }

    private float tempC = DEFAULT_TEMP_C;
    private Condition condition = DEFAULT_CONDITION;
    private MoonPhase moonPhase = DEFAULT_MOON_PHASE;
    private Time sunrise = DEFAULT_SUNRISE;
    private Time sunset = DEFAULT_SUNSET;
    private int windKph = DEFAULT_WIND_KPH;
    private long lastUpdate;

    @Nullable
    private WeatherReceiver weatherReceiver;

    public Weather() {}

    protected Weather(Parcel in) {
        readFromParcel(in);
    }

    protected Weather(Context context, String action) {
        weatherReceiver = new WeatherReceiver(context, action, this);
        weatherReceiver.register();
        restore(context);
    }

    @Override
    protected void finalize() throws Throwable {
        if (weatherReceiver != null) weatherReceiver.unregister();
        super.finalize();
    }

    @Override
    public void readFromParcel(Parcel in) {
        tempC = in.readFloat();
        condition = (Condition) in.readSerializable();
        moonPhase = (MoonPhase) in.readSerializable();
        sunrise = in.readParcelable(Time.class.getClassLoader());
        sunset = in.readParcelable(Time.class.getClassLoader());
        windKph = in.readInt();
        lastUpdate = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeFloat(tempC);
        out.writeSerializable(condition);
        out.writeSerializable(moonPhase);
        out.writeParcelable(sunrise, 0);
        out.writeParcelable(sunset, 0);
        out.writeInt(windKph);
        out.writeLong(lastUpdate);
    }

    protected void setCelsius(float celsius) {
        tempC = celsius;
    }

    public float getCelsius() {
        return tempC;
    }

    public float getFahrenheit() {
        return (int) (9f / 5f * tempC + 32);
    }

    protected void setCondition(Condition condition) {
        this.condition = condition;
    }

    @NonNull
    public Condition getCondition() {
        if (sCondition != null) {
            return sCondition;
        }
        if (condition == null) {
            return DEFAULT_CONDITION;
        }
        return condition;
    }

    protected void setMoonPhase(MoonPhase moonPhase) {
        this.moonPhase = moonPhase;
    }

    @NonNull
    public MoonPhase getMoonPhase() {
        if (moonPhase == null) {
            return DEFAULT_MOON_PHASE;
        }
        return moonPhase;
    }

    protected void setSunrise(Time sunrise) {
        this.sunrise = sunrise;
    }

    @NonNull
    public Time getSunrise() {
        if (sSunrise != null) {
            return sSunrise;
        }
        if (sunrise == null) {
            return DEFAULT_SUNRISE;
        }
        return sunrise;
    }

    protected void setSunset(Time sunset) {
        this.sunset = sunset;
    }

    @NonNull
    public Time getSunset() {
        if (sSunset != null) {
            return sSunset;
        }
        if (sunset == null) {
            return DEFAULT_SUNSET;
        }
        return sunset;
    }

    protected void setWindKph(int windKph) {
        this.windKph = windKph;
    }

    public int getWindKph() {
        return windKph;
    }

    public int getWindMph() {
        return (int) (windKph * 0.6214f);
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    private SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(getClass().getSimpleName(), Context.MODE_PRIVATE);
    }

    /**
     * Saves current state to disk, to be restored later with {@link #restore(Context)}
     */
    public void save(Context context) {
        lastUpdate = System.currentTimeMillis();
        getSharedPreferences(context).edit().putString(BUNDLE_STATE, ParcelableUtils.toString(this)).apply();
    }

    /**
     * Restores states that was previously saved in {@link #save(Context)}
     */
    public void restore(Context context) {
        ParcelableUtils.fromString(getSharedPreferences(context).getString(BUNDLE_STATE, null), this);
    }

    /**
     * Fetches the latest known weather. Returns false if currently unable.
     */
    @WorkerThread
    protected abstract boolean fetch(Context context, Object... args);

    private static synchronized void invalidateCalendar() {
        CALENDAR = null;
    }

    private static synchronized Calendar getCurrentCalendar() {
        if (CALENDAR == null) {
            CALENDAR = Calendar.getInstance();
        }
        if (sFakeTime >= 0) {
            CALENDAR.setTimeInMillis(sFakeTime);
        }
        return CALENDAR;
    }

    private int getCurrentMinute() {
        return getCurrentCalendar().get(Calendar.MINUTE);
    }

    private int getCurrentHour() {
        return getCurrentCalendar().get(Calendar.HOUR_OF_DAY);
    }

    public boolean before(Time time) {
        return before(time.getHour(), time.getMinute());
    }

    public boolean before(int hour, int minute) {
        int h = getCurrentHour();
        int m = getCurrentMinute();

        if(h < hour) return true;
        if(h == hour && m < minute) return true;
        return false;
    }

    public boolean after(Time time) {
        return after(time.getHour(), time.getMinute());
    }

    public boolean after(int hour, int minute) {
        int h = getCurrentHour();
        int m = getCurrentMinute();

        if(h > hour) return true;
        if(h == hour && m > minute) return true;
        return false;
    }

    public boolean equal(Time time) {
        return equal(time.getHour(), time.getMinute());
    }

    public boolean equal(int hour, int minute) {
        int h = getCurrentHour();
        int m = getCurrentMinute();
        return h == hour && m == minute;
    }

    public boolean isNight() {
        return !isSunrise() && !isDay() && !isSunset();
    }

    public boolean isSunrise() {
        invalidateCalendar();
        boolean after = after(getSunrise().getHour() - 1, getSunrise().getMinute());
        boolean before = before(getSunrise().getHour() + 1, getSunrise().getMinute());
        return after && before;
    }

    public boolean isDay() {
        invalidateCalendar();
        boolean after = after(getSunrise().getHour() + 1, getSunrise().getMinute());
        boolean before = before(getSunset().getHour() - 1, getSunset().getMinute());
        return (after && before)
                || equal(getSunset().getHour() - 1, getSunset().getMinute())
                || equal(getSunrise().getHour() + 1, getSunrise().getMinute());
    }

    public boolean isSunset() {
        invalidateCalendar();
        boolean after = after(getSunset().getHour() - 1, getSunset().getMinute());
        boolean before = before(getSunset().getHour() + 1, getSunset().getMinute());
        return after && before;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o instanceof Weather) {
            Weather a = (Weather) o;
            return Objects.equals(tempC, a.tempC)
                    && Objects.equals(condition, a.condition)
                    && Objects.equals(moonPhase, a.moonPhase)
                    && Objects.equals(sunrise, a.sunrise)
                    && Objects.equals(sunset, a.sunset)
                    && Objects.equals(windKph, a.windKph)
                    && Objects.equals(lastUpdate, a.lastUpdate);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tempC, condition, moonPhase, sunrise, sunset, windKph, lastUpdate);
    }

    @Override
    public String toString() {
        return String.format("Weather{tempC=%s, condition=%s, moonPhase=%s, sunrise=%s, sunset=%s, windKph=%s, lastUpdate=%s}",
                tempC, condition, moonPhase, sunrise, sunset, windKph, SimpleDateFormat.getDateTimeInstance().format(new Date(lastUpdate)));
    }

    /**
     * A BroadcastReceiver that listens to weather updates and invalidates the Weather object
     * whenever things change. We use a WeakReference and register to the Application context
     * because we're not tied to any specific Activity/Service lifecycle. The Weather object we're
     * tied to is expected to unregister us when they are destroyed.
     * */
    private static final class WeatherReceiver extends BroadcastReceiver {
        private final Context context;
        private final String action;
        private final WeakReference<Weather> weakWeather;

        WeatherReceiver(Context context, String action, Weather weather) {
            this.context = context.getApplicationContext();
            this.action = action;
            this.weakWeather = new WeakReference<>(weather);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Weather weather = weakWeather.get();
            if (weather == null) {
                unregister();
                return;
            }

            weather.restore(context);
        }

        void register() {
            ContextCompat.registerReceiver(context, this, new IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED);
        }

        void unregister() {
            try {
                context.unregisterReceiver(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
