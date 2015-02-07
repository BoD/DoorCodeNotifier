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
package org.jraf.android.dcn.wearable.app.notif;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import org.jraf.android.dcn.R;
import org.jraf.android.dcn.common.wear.WearHelper;
import org.jraf.android.util.log.wrapper.Log;

public class NotificationService extends WearableListenerService {
    private static final int NOTIFICATION_ID = 0;
    private WearHelper mWearHelper = WearHelper.get();

    public NotificationService() {}

    @Override
    public void onPeerConnected(Node peer) {}

    @Override
    public void onPeerDisconnected(Node peer) {}

    @Override
    public void onMessageReceived(MessageEvent messageEvent) { }

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
            } else {
                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap dataMap = dataMapItem.getDataMap();
                String title = dataMap.getString(WearHelper.EXTRA_TITLE);
                String text = dataMap.getString(WearHelper.EXTRA_TEXT);
                Asset photoAsset = dataMap.getAsset(WearHelper.EXTRA_PHOTO);
                Bitmap photo = null;
                if (photoAsset != null) {
                    // Blocking
                    mWearHelper.connect(this);
                    // Blocking
                    photo = mWearHelper.loadBitmapFromAsset(photoAsset);
                }
                showNotification(title, text, photo);
            }
        }
    }

    private void dismissNotification() {
        Log.d();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void showNotification(String title, String text, Bitmap photo) {
        Log.d();
        Notification.Builder mainNotifBuilder = new Notification.Builder(this);

        // A small icon is mandatory even if it will be hidden - without this the system refuses to show the notification...
        mainNotifBuilder.setSmallIcon(R.drawable.ic_launcher);

        // Title
        SpannableString spannableTitle = new SpannableString(title);
        Object span = new TextAppearanceSpan(this, R.style.NotificationContentTitleTextAppearance);
        spannableTitle.setSpan(span, 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mainNotifBuilder.setContentTitle(spannableTitle);

        // Text
        SpannableString spannableText = new SpannableString(text);
        span = new TextAppearanceSpan(this, R.style.NotificationContentTextTextAppearance);
        spannableText.setSpan(span, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mainNotifBuilder.setContentText(spannableText);

        // Wear specifics
        Notification.WearableExtender wearableExtender = new Notification.WearableExtender();
        if (photo != null) wearableExtender.setBackground(photo);
        Notification.Builder wearableNotifBuilder = wearableExtender.extend(mainNotifBuilder);
        Notification notification = wearableNotifBuilder.build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        mWearHelper.disconnect();
        super.onDestroy();
    }
}
