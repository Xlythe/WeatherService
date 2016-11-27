package com.xlythe.service.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.WorkerThread;

import java.io.Serializable;
import java.util.Calendar;

public abstract class Weather implements Parcelable {
    private static final String BUNDLE_STATE = "state:";

    public enum Condition {
        SNOW, RAIN, CLOUDY, SUNNY;
    }

    public enum MoonPhase {
        NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS,
        FULL_MOON, WANING_GIBBOUS, THIRD_QUARTER, WANING_CRESCENT;
    }

    public static class Time implements Serializable {
        private final int hour;
        private final int minute;

        public Time(int hour, int minute) {
            this.hour = hour;
            this.minute = minute;
        }

        public int getHour() {
            return hour;
        }

        public int getMinute() {
            return minute;
        }

        @Override
        public boolean equals(Object o) {
            if (o != null && o instanceof Time) {
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
            return hour + ":" + minute;
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

    private float tempC;
    private Condition condition;
    private MoonPhase moonPhase;
    private Time sunrise;
    private Time sunset;
    private int windKph;
    private long lastUpdate;

    public Weather() {}

    protected Weather(Parcel in) {
        readFromParcel(in);
    }

    protected void readFromParcel(Parcel in) {
        tempC = in.readFloat();
        condition = (Condition) in.readSerializable();
        moonPhase = (MoonPhase) in.readSerializable();
        sunrise = (Time) in.readSerializable();
        sunset = (Time) in.readSerializable();
        windKph = in.readInt();
        lastUpdate = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeFloat(tempC);
        out.writeSerializable(condition);
        out.writeSerializable(moonPhase);
        out.writeSerializable(sunrise);
        out.writeSerializable(sunset);
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

    public Condition getCondition() {
        if (sCondition != null) {
            return sCondition;
        }
        return condition;
    }

    protected void setMoonPhase(MoonPhase moonPhase) {
        this.moonPhase = moonPhase;
    }

    public MoonPhase getMoonPhase() {
        return moonPhase;
    }

    protected void setSunrise(Time sunrise) {
        this.sunrise = sunrise;
    }

    public Time getSunrise() {
        if (sSunrise != null) {
            return sSunrise;
        }
        return sunrise;
    }

    protected void setSunset(Time sunset) {
        this.sunset = sunset;
    }

    public Time getSunset() {
        if (sSunset != null) {
            return sSunset;
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

    private String getStateKey() {
        return BUNDLE_STATE + getClass().getSimpleName();
    }

    /**
     * Saves current state to disk, to be restored later with {@link #restore(Context)}
     */
    public void save(Context context) {
        lastUpdate = System.currentTimeMillis();
        Parcel parcel = Parcel.obtain();
        parcel.writeValue(this);
        getSharedPreferences(context).edit().putString(getStateKey(), new String(parcel.marshall())).apply();
        parcel.recycle();
    }

    /**
     * Restores states that was previously saved in {@link #save(Context)}
     */
    public void restore(Context context) {
        String data = getSharedPreferences(context).getString(getStateKey(), null);
        if (data == null) {
            return;
        }
        byte[] bytes = data.getBytes();
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        readFromParcel(parcel);
        parcel.recycle();
    }

    /**
     * Fetches the latest known weather. Returns false if currently unable.
     */
    @WorkerThread
    protected abstract boolean fetch(Context context, Object... args);

    private static Calendar CALENDAR;

    private static void invalidateCalendar() {
        CALENDAR = null;
    }

    private static Calendar getCurrentCalendar() {
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
        invalidateCalendar();;
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
    public boolean equals(Object o) {
        if (o != null && o instanceof Weather) {
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
                tempC, condition, moonPhase, sunrise, sunset, windKph, lastUpdate);
    }
}
