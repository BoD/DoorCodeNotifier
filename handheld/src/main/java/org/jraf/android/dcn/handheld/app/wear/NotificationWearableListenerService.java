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
package org.jraf.android.dcn.handheld.app.wear;

import android.content.Intent;
import android.net.Uri;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import org.jraf.android.dcn.R;
import org.jraf.android.dcn.common.wear.WearHelper;
import org.jraf.android.dcn.handheld.app.geofencing.GeofencingService;
import org.jraf.android.util.log.wrapper.Log;
import org.jraf.android.util.parcelable.ParcelableUtil;

import java.io.UnsupportedEncodingException;

public class NotificationWearableListenerService extends WearableListenerService {
    public NotificationWearableListenerService() {}

    @Override
    public void onPeerConnected(Node peer) {}

    @Override
    public void onPeerDisconnected(Node peer) {}

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(messageEvent.toString());
        byte[] payload = messageEvent.getData();
        switch (messageEvent.getPath()) {
            case WearHelper.PATH_NOTIFICATION_ACTION_OPEN:
                Uri contactUri = ParcelableUtil.unparcel(payload, Uri.CREATOR);
                openContact(contactUri);
                break;

            case WearHelper.PATH_NOTIFICATION_ACTION_CALL:
                String phoneNumber = null;
                try {
                    phoneNumber = new String(payload, "utf-8");
                } catch (UnsupportedEncodingException ignored) {
                    // Can never happen
                }
                call(phoneNumber);
                break;

            case WearHelper.PATH_NOTIFICATION_ACTION_SMS:
                phoneNumber = null;
                try {
                    phoneNumber = new String(payload, "utf-8");
                } catch (UnsupportedEncodingException ignored) {
                    // Can never happen
                }
                sms(phoneNumber);
                break;
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d("count=" + dataEvents.getCount());
        // There should always be only one item, but we iterate to be safe
        for (DataEvent dataEvent : dataEvents) {
            DataItem dataItem = dataEvent.getDataItem();
            Uri uri = dataItem.getUri();
            Log.d("uri=" + uri);
            String path = uri.getPath();
            Log.d("path=" + path);
            if (dataEvent.getType() == DataEvent.TYPE_DELETED) {
                dismissNotification();
            }
        }
    }

    /**
     * The Wear notification has been dismissed: dismiss the handheld one.
     */
    private void dismissNotification() {
        Log.d();
        Intent intent = new Intent(GeofencingService.ACTION_DISMISS_NOTIFICATION, null, this, GeofencingService.class);
        startService(intent);
    }

    private void openContact(Uri contactUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(contactUri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void call(String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phoneNumber));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void sms(String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("sms:" + phoneNumber));
        intent.putExtra("sms_body", getString(R.string.notification_action_sms_body));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
