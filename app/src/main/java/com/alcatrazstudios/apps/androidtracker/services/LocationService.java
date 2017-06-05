package com.alcatrazstudios.apps.androidtracker.services;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.alcatrazstudios.apps.androidtracker.R;
import com.alcatrazstudios.apps.androidtracker.Utilities.LoopjHttpClient;
import com.alcatrazstudios.apps.androidtracker.model.GpsTrackerEvent;
import com.alcatrazstudios.apps.androidtracker.model.TrackerLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import io.realm.Realm;

//import com.google.android.gms.common.GooglePlayServicesUtil;

public class LocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "LocationService";
    private static final float minDistance=50.0f;
    private static final float minAccuracy=50.0f;
    private static final int maxTries=10;
    private Location maxAccuracyLocation;

    private int tries=0;

    // use the websmithing defaultUploadWebsite for testing and then check your
    // location with your browser here: https://www.websmithing.com/gpstracker/displaymap.php
    private String defaultUploadWebsite;

    private boolean currentlyProcessingLocation = false;
    private LocationRequest locationRequest;
    private GoogleApiClient googleApiClient;
    private SharedPreferences sharedPreferences;

    private Realm realm;

    public LocationService(){
        super();
        Log.e(TAG,"Constructor");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG,"onCreate Started");
        realm = Realm.getDefaultInstance();
        defaultUploadWebsite = getString(R.string.default_upload_website);
        sharedPreferences = this.getSharedPreferences("com.alcatrazstudios.apps.androidtracker.prefs", Context.MODE_PRIVATE);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG,"onStartCommand already started =" + currentlyProcessingLocation);
        // if we are currently trying to get a location and the alarm manager has called this again,
        // no need to start processing a new location.
        if (!currentlyProcessingLocation) {
            currentlyProcessingLocation = true;
            maxAccuracyLocation = null;
            startTracking();
        }

        return START_NOT_STICKY;
    }

    private void startTracking() {
        Log.e(TAG, "startTracking");

        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {

            googleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            if (!googleApiClient.isConnected() || !googleApiClient.isConnecting()) {
                googleApiClient.connect();
            }
        } else {
            Log.e(TAG, "unable to connect to google play services.");
            stopSelf();
        }
    }

    protected void insertLocationToDB(Location location){

        SharedPreferences.Editor editor = sharedPreferences.edit();
        boolean firstTimeGettingPosition = isFirstTimeGettingPosition();

        if (firstTimeGettingPosition) {
            editor.putBoolean("firstTimeGettingPosition", false);
        }

        editor.putFloat("previousLatitude", (float) location.getLatitude());
        editor.putFloat("previousLongitude", (float) location.getLongitude());
        editor.apply();

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Double.class, new JsonSerializer<Double>() {
            @Override
            public JsonElement serialize(Double originalValue, Type typeOf, JsonSerializationContext context) {
                BigDecimal bigValue = BigDecimal.valueOf(originalValue).setScale(7, RoundingMode.CEILING);
                return new JsonPrimitive(bigValue.toPlainString());
            }
        });

        Gson gson = gsonBuilder.create();
        realm.beginTransaction();
        TrackerLocation trackerLocation = new TrackerLocation(location);
        String jsonString = gson.toJson(trackerLocation);
        GpsTrackerEvent gpsTrackerEvent = new GpsTrackerEvent(1, jsonString, trackerLocation.getDate());

        realm.copyToRealm(gpsTrackerEvent);
        realm.commitTransaction();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG,"onDestroy");
        stopLocationUpdates();
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            Log.e(TAG, "position: " + location.getLatitude() + ", " + location.getLongitude() + " accuracy: " + location.getAccuracy());

            // we have our desired accuracy of 500 meters so lets quit this service,
            // onDestroy will be called and stop our location uodates
            if (location.getAccuracy() <= minAccuracy && getDistanceToLastLocation(location) >=minDistance) {
                stopLocationUpdates();
                //  sendLocationDataToWebsite(location);
                insertLocationToDB(location);
            } else {

                tries++;
                if (tries>=maxTries){
                    stopSelf();
                }
            }
        }
    }

    private float getDistanceToLastLocation(Location location){
        if (!isFirstTimeGettingPosition()) {
            Location previousLocation = new Location("");
            previousLocation.setLatitude(sharedPreferences.getFloat("previousLatitude", 0f));
            previousLocation.setLongitude(sharedPreferences.getFloat("previousLongitude", 0f));

            return location.distanceTo(previousLocation);
        }else{
            return 999999999.9f;
        }
    }
    private boolean isFirstTimeGettingPosition(){
        return sharedPreferences.getBoolean("firstTimeGettingPosition", true);
    }

    private void stopLocationUpdates() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    /**
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
     * request the current location or start periodic updates
     */
    @Override
    public void onConnected(Bundle bundle) {
        Log.e(TAG, "onConnected");

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(1000); // milliseconds
        locationRequest.setFastestInterval(1000); // the fastest rate in milliseconds at which your app can handle location updates
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed");

        stopLocationUpdates();
        stopSelf();

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "GoogleApiClient connection has been suspend");
    }



}
