/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2015 Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jraf.android.dcn.handheld.app.geofencing;

import java.util.List;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.WorkerThread;

import org.jraf.android.util.log.wrapper.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

/**
 * Helper singleton class to deal with the geofencing APIs.<br/>
 * Note: {@link #connect(android.content.Context)} must be called prior to calling all the other methods.
 */
public class GeofencingHelper {
    private static final GeofencingHelper INSTANCE = new GeofencingHelper();

    private Context mContext;
    private GoogleApiClient mGoogleApiClient;

    private GeofencingHelper() {}

    public static GeofencingHelper get() {
        return INSTANCE;
    }

    @WorkerThread
    public synchronized void connect(Context context) {
        Log.d();
        if (mGoogleApiClient != null) {
            Log.d("Already connected");
            return;
        }

        mContext = context.getApplicationContext();
        mGoogleApiClient = new GoogleApiClient.Builder(context).addApi(LocationServices.API).build();
        // Blocking
        ConnectionResult connectionResult = mGoogleApiClient.blockingConnect();
        if (!connectionResult.isSuccess()) {
            Log.w("Could not connect to Google Api Client (error code: " + connectionResult.getErrorCode() + ")"); mGoogleApiClient = null;
        }
    }

    private boolean isConnected() {
        return mGoogleApiClient != null;
    }

    public synchronized void disconnect() {
        Log.d();
        if (mGoogleApiClient != null) mGoogleApiClient.disconnect();
        mGoogleApiClient = null;
    }

    @WorkerThread
    public void removeAllGeofences() {
        Log.d(); if (!isConnected()) return;
        LocationServices.GeofencingApi.removeGeofences(mGoogleApiClient, getPendingIntent()).await();
        Log.d("All geofences removed");
    }

    @WorkerThread
    public void addGeofences(List<Geofence> geofenceList) {
        Log.d(); if (!isConnected()) return;
        if (geofenceList.isEmpty()) {
            Log.d("List of geofences is empty: do nothing");
            return;
        }
        GeofencingRequest.Builder requestBuilder = new GeofencingRequest.Builder();
        requestBuilder.addGeofences(geofenceList);
        LocationServices.GeofencingApi.addGeofences(mGoogleApiClient, requestBuilder.build(), getPendingIntent()).await();
        Log.d(geofenceList.size() + " geofences added");
    }

    private PendingIntent getPendingIntent() {
        Intent intent = new Intent(mContext, GeofencingService.class);
        return PendingIntent.getService(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}