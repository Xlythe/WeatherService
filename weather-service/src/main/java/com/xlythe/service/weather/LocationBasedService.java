package com.xlythe.service.weather;

import android.Manifest;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

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

/**
 * A service that first grabs the user's most recent location before querying a website.
 */
public abstract class LocationBasedService extends GcmTaskService {
    private static final String TAG = LocationBasedService.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final String ACTION_RUN_MANUALLY = "com.xlythe.service.ACTION_RUN_MANUALLY";
    private static final long LOCATION_TIMEOUT_IN_SECONDS = 10;
    private static final int NETWORK_TIMEOUT_IN_MILLIS = 10 * 1000;
    private static final HandlerThread sBackgroundThread = new HandlerThread("ServiceBackgroundThread");

    static {
        sBackgroundThread.start();
    }

    private Handler mHandler;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();
        if (DEBUG) Log.d(TAG, "onStartCommand() action=" + action);
        if (ACTION_RUN_MANUALLY.equals(action)) {
            if (!ensureHandler()) {
                Log.d(TAG, "Attempted to manually run task, but already running.");
                return START_NOT_STICKY;
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onRunTask(new TaskParams(action));
                    stopSelf();
                }
            });
            return START_NOT_STICKY;
        } else {
            return super.onStartCommand(intent, flags, startId);
        }
    }

    private synchronized boolean ensureHandler() {
        if (mHandler == null) {
            mHandler = new Handler(sBackgroundThread.getLooper());
            return true;
        }
        return false;
    }

    @Override
    public int onRunTask(final TaskParams params) {
        if (Looper.myLooper() != sBackgroundThread.getLooper()) {
            if (DEBUG) Log.w(TAG, "onRunTask called on wrong thread. Moving to a safe thread.");
            ensureHandler();
            final StatusCountDownLatch latch = new StatusCountDownLatch(1);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    latch.setStatus(onRunTask(params));
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return latch.getStatus();
        }

        if (DEBUG) Log.d(TAG, "Building GoogleApiClient");
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(LocationServices.API)
                .build();
        try {
            if (!googleApiClient.blockingConnect().isSuccess()) {
                return GcmNetworkManager.RESULT_RESCHEDULE;
            }

            if (DEBUG) Log.d(TAG, "Connected to LocationServices API");
            final Location location = getLocation(googleApiClient);
            if (location == null) {
                if (DEBUG) Log.d(TAG, "No location found");
                return GcmNetworkManager.RESULT_RESCHEDULE;
            }

            try {
                if (DEBUG) Log.d(TAG, "Creating url");
                String requestUrl = createUrl(location);
                if (DEBUG) Log.d(TAG, requestUrl);

                if (DEBUG) Log.d(TAG, "Fetching url");
                String input = fetch(requestUrl);
                if (DEBUG) Log.d(TAG, input);

                parse(input);
            } catch (IOException e) {
                if (DEBUG) Log.e(TAG, "IO Exception", e);
                return GcmNetworkManager.RESULT_RESCHEDULE;
            } catch (JSONException e) {
                if (DEBUG) Log.e(TAG, "JSON Exception", e);
            }
        } finally {
            if (googleApiClient.isConnected()) {
                googleApiClient.disconnect();
            }
        }

        return GcmNetworkManager.RESULT_SUCCESS;
    }

    @SuppressWarnings({"MissingPermission"})
    private Location getLocation(GoogleApiClient googleApiClient) {
        if (!PermissionUtils.hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return null;
        }

        Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if (location != null) {
            if (DEBUG) Log.d(TAG, "Found cached location");
            return location;
        }

        try {
            if (DEBUG) Log.d(TAG, "Querying for a new location");
            CountDownLatch latch = new CountDownLatch(1);
            LastKnownLocationCallback callback = new LastKnownLocationCallback(latch);
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    googleApiClient,
                    LocationRequest
                            .create()
                            .setNumUpdates(1)
                            .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY),
                    callback);
            try {
                latch.await(LOCATION_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for location.", e);
            }
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, callback);
            return callback.location;
        } catch (IllegalStateException e) {
            // We were disconnected while we were attempting to use the API.
            Log.e(TAG, "Requesting location updates failed. GoogleApiClient was disconnected.", e);
        }

        return null;
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
                        sb.append(line + "\n");
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

    private static class LastKnownLocationCallback implements LocationListener {
        private final CountDownLatch latch;
        private Location location;

        LastKnownLocationCallback(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onLocationChanged(Location location) {
            this.location = location;
            latch.countDown();
        }
    }

    private static class StatusCountDownLatch extends CountDownLatch {
        private int status = -1;

        StatusCountDownLatch(int count) {
            super(count);
        }

        int getStatus() {
            return status;
        }

        void setStatus(int status) {
            this.status = status;
        }
    }    public static class Builder {
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
