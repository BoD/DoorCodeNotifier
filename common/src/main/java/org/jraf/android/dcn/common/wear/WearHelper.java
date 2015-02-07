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
package org.jraf.android.dcn.common.wear;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.jraf.android.util.annotation.Background;
import org.jraf.android.util.log.wrapper.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Helper singleton class to deal with the wear APIs.<br/>
 * Note: {@link #connect(android.content.Context)} must be called prior to calling all the other methods.
 */
public class WearHelper {
    private static final WearHelper INSTANCE = new WearHelper();
    public static final String PATH_NOTIFICATION = "/notification";
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_TEXT = "EXTRA_TEXT";
    public static final String EXTRA_PHOTO = "EXTRA_PHOTO";

    private Context mContext;
    private GoogleApiClient mGoogleApiClient;

    private WearHelper() {}

    public static WearHelper get() {
        return INSTANCE;
    }

    @Background(Background.Type.NETWORK)
    public synchronized void connect(Context context) {
        Log.d();
        if (mGoogleApiClient != null) {
            Log.d("Already connected");
            return;
        }

        mContext = context.getApplicationContext();
        mGoogleApiClient = new GoogleApiClient.Builder(context).addApi(Wearable.API).build();
        // Blocking
        ConnectionResult connectionResult = mGoogleApiClient.blockingConnect();
        if (!connectionResult.isSuccess()) {
            // TODO handle failures
        }
    }

    public synchronized void disconnect() {
        Log.d();
        if (mGoogleApiClient != null) mGoogleApiClient.disconnect();
        mGoogleApiClient = null;
    }

    @Background(Background.Type.NETWORK)
    public void putNotification(String title, String text, @Nullable Bitmap photo) {
        Log.d();
        // First remove any old notification
        Wearable.DataApi.deleteDataItems(mGoogleApiClient, createUri(PATH_NOTIFICATION)).await();

        // Create new notification
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_NOTIFICATION);

        DataMap dataMap = putDataMapRequest.getDataMap();
        dataMap.putString(EXTRA_TITLE, title);
        dataMap.putString(EXTRA_TEXT, text);
        if (photo != null) dataMap.putAsset(EXTRA_PHOTO, createAssetFromBitmap(photo));

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request).await();
    }

    @Background(Background.Type.NETWORK)
    public void removeNotification() {
        Log.d();
        Wearable.DataApi.deleteDataItems(mGoogleApiClient, createUri(PATH_NOTIFICATION)).await();
    }


    /*
     * Misc.
     */

    private static Uri createUri(String path) {
        return new Uri.Builder().scheme("wear").path(path).build();
    }

    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    @Background(Background.Type.NETWORK)
    public Bitmap loadBitmapFromAsset(Asset asset) {
        DataApi.GetFdForAssetResult fd = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).await();
        InputStream inputStream = fd.getInputStream();
        return BitmapFactory.decodeStream(inputStream);
    }
}
