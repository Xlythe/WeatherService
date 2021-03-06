package com.xlythe.service.weather;

import android.Manifest;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A service that first grabs the user's most recent location before querying a website.
 */
public abstract class LocationBasedService extends WeatherService {
    private static final String TAG = LocationBasedService.class.getSimpleName();

    private static final long LOCATION_TIMEOUT_IN_SECONDS = 10;
    private static final int NETWORK_TIMEOUT_IN_MILLIS = 10 * 1000;

    private Bundle mParams;

    @Override
    public Result onRunTask(Bundle params) {
        Location location = getLocation();
        if (location == null) {
            if (DEBUG) Log.d(TAG, "No location found");
            return Result.RESCHEDULE;
        }

        try {
            mParams = params;

            if (DEBUG) Log.d(TAG, "Creating url");
            String requestUrl = createUrl(location);
            if (DEBUG) Log.d(TAG, requestUrl);

            if (DEBUG) Log.d(TAG, "Fetching url");
            String input = fetch(requestUrl);
            if (DEBUG) Log.d(TAG, input);

            parse(input);
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "IO Exception", e);
            return Result.RESCHEDULE;
        } catch (JSONException e) {
            if (DEBUG) Log.e(TAG, "JSON Exception", e);
        }

        return Result.SUCCESS;
    }

    protected Bundle getParams() {
        return mParams;
    }

    @Nullable
    @SuppressWarnings({"MissingPermission"})
    private Location getLocation() {
        if (!PermissionUtils.hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return null;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(this);

        CountDownLatch latch = new CountDownLatch(1);
        LastKnownLocationCallback callback = new LastKnownLocationCallback(latch);
        client.getLastLocation().addOnCompleteListener(callback);
        try {
            if (!latch.await(LOCATION_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                Log.w(TAG, "Timed out waiting for a cached location after " + LOCATION_TIMEOUT_IN_SECONDS + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Interrupted while waiting for location.", e);
        }

        if (callback.location != null) {
            if (DEBUG) Log.d(TAG, "Found cached location");
            return callback.location;
        }

        if (DEBUG) Log.d(TAG, "Querying for a new location");
        latch = new CountDownLatch(1);
        callback = new LastKnownLocationCallback(latch);
        client.requestLocationUpdates(
                LocationRequest
                        .create()
                        .setNumUpdates(1)
                        .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY),
                callback,
                Looper.getMainLooper()
        );
        try {
            if (!latch.await(LOCATION_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                Log.w(TAG, "Timed out waiting for a location after " + LOCATION_TIMEOUT_IN_SECONDS + " seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Interrupted while waiting for location.", e);
        }
        client.removeLocationUpdates(callback);
        return callback.location;
    }

    protected String createUrl(Location location) {
        return createUrl(location.getLatitude(), location.getLongitude());
    }

    protected abstract String createUrl(double latitude, double longitude);

    protected abstract void parse(String json) throws JSONException;

    private String fetch(String url) throws IOException {
        HttpURLConnection c = null;
        try {
            URL u = new URL(url);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Content-length", "0");
            c.setUseCaches(false);
            c.setAllowUserInteraction(false);
            c.setConnectTimeout(NETWORK_TIMEOUT_IN_MILLIS);
            c.setReadTimeout(NETWORK_TIMEOUT_IN_MILLIS);
            c.connect();
            int status = c.getResponseCode();

            switch (status) {
                case 200:
                case 201:
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                        sb.append("\n");
                    }
                    br.close();
                    return sb.toString();
                default:
                    throw new IOException("Call to server failed with status " + status);
            }
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class LastKnownLocationCallback extends LocationCallback implements OnCompleteListener<Location> {
        private final CountDownLatch latch;
        private Location location;

        LastKnownLocationCallback(CountDownLatch latch) {
            this.latch = latch;
        }

        public void onLocationResult(LocationResult result) {
            location = getLocation(result.getLocations());
            if (location != null) {
                latch.countDown();
            }
        }

        @Nullable
        private static Location getLocation(@Nullable List<Location> locations) {
            if (locations == null || locations.isEmpty()) {
                return null;
            }
            return locations.get(0);
        }

        @Override
        public void onComplete(@NonNull Task<Location> task) {
            if (task.isSuccessful()) {
                location = task.getResult();
            }
            latch.countDown();
        }
    }

    public static class Builder {
        private final List<Pair<String, String>> params = new ArrayList<>();
        private String url;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder param(String key, String value) {
            params.add(new Pair<>(key, value));
            return this;
        }

        private void verify() {
            if (url == null) {
                throw new IllegalStateException("Missing url");
            }
        }

        private String params() {
            StringBuilder toString = new StringBuilder();
            for (Pair<String, String> param : params) {
                // Already added a param before. Append '&'.
                if (toString.length() > 0) {
                    toString.append('&');
                }

                // Add the params
                toString.append(param.first);
                toString.append('=');
                toString.append(param.second);
            }
            if (toString.length() > 0) {
                toString.insert(0, '?');
            }
            return toString.toString();
        }

        public String build() {
            verify();
            return url + params();
        }
    }
}
