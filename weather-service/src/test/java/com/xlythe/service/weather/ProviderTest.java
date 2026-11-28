package com.xlythe.service.weather;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class ProviderTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void openWeatherConvertsKelvinAndMetersPerSecond() {
        OpenWeather weather = new OpenWeather();
        assertTrue(weather.fetch(context, "{\"main\":{\"temp\":273.15},\"wind\":{\"speed\":4.5},"
                + "\"weather\":[{\"main\":\"Drizzle\"}],\"sys\":{\"sunrise\":1000,\"sunset\":2000}}"));
        assertEquals(0f, weather.getCelsius(), 0.001f);
        assertEquals(16, weather.getWindKph());
        assertEquals(Weather.Condition.RAIN, weather.getCondition());
    }

    @Test public void pirateWeatherRetainsFractionalMoonPhase() {
        PirateWeather weather = new PirateWeather();
        assertTrue(weather.fetch(context, "{\"daily\":{\"data\":[{\"moonPhase\":0.75,\"sunriseTime\":1000,\"sunsetTime\":2000}]}}"));
        assertEquals(Weather.MoonPhase.THIRD_QUARTER, weather.getMoonPhase());
    }

    @Test public void currentOnlyForecastProvidesWindAndTemperature() {
        PirateWeather weather = new PirateWeather();
        assertTrue(weather.fetch(context, "{\"currently\":{\"temperature\":-5,\"summary\":\"Snow\",\"windSpeed\":10}}"));
        assertEquals(-5f, weather.getCelsius(), 0f);
        assertEquals(36, weather.getWindKph());
        assertEquals(Weather.Condition.SNOW, weather.getCondition());
    }

    @Test public void providerErrorIsNotAWeatherReport() {
        assertFalse(new PirateWeather().fetch(context, "{\"error\":\"unavailable\"}"));
        assertFalse(new OpenWeather().fetch(context, "{\"cod\":401}"));
    }

    @Test public void coordinatesAndKeyRemainDistinctPathSegments() {
        Uri url = Uri.parse(PirateWeatherService.forecastUrl("key/one", -45.5, 120.25));
        assertEquals("key/one", url.getPathSegments().get(1));
        assertEquals("-45.5,120.25", url.getLastPathSegment());
        assertEquals("si", url.getQueryParameter("units"));
    }

    @Test public void manualRefreshHasCredentialsBeforeScheduling() {
        new PirateWeatherProvider(context, "one");
        assertEquals("one", PirateWeatherService.getApiKey(context));
        new PirateWeatherProvider(context, "two");
        assertEquals("two", PirateWeatherService.getApiKey(context));
    }

    @Test public void queryValuesDoNotOverrideOtherParameters() {
        Uri url = Uri.parse(new LocationBasedService.Builder().url("https://example.org/weather?units=si")
                .param("key", "one&units=us").build());
        assertEquals("one&units=us", url.getQueryParameter("key"));
        assertEquals("si", url.getQueryParameter("units"));
    }

    @Test public void freshnessUsesMillisecondsAndRejectsFutureTimestamps() {
        long now = 86400000L;
        assertTrue(WeatherService.isFresh(now - 3600000, now, 7200, 1800));
        assertFalse(WeatherService.isFresh(now - 5400000, now, 7200, 1800));
        assertFalse(WeatherService.isFresh(now + 1000, now, 7200, 1800));
    }

    @Test public void workDataRoundTripsScalarAndArrayExtras() {
        Bundle extras = new Bundle();
        extras.putBoolean("manual", true);
        extras.putLong("id", 12345678901L);
        extras.putIntArray("days", new int[] {1, 5});
        extras.putBooleanArray("flags", new boolean[] {true, false});
        Bundle result = WeatherService.toBundle(WeatherService.toData(extras));
        assertTrue(result.getBoolean("manual"));
        assertEquals(12345678901L, result.getLong("id"));
        assertArrayEquals(extras.getIntArray("days"), result.getIntArray("days"));
        assertArrayEquals(extras.getBooleanArray("flags"), result.getBooleanArray("flags"));
    }
}
