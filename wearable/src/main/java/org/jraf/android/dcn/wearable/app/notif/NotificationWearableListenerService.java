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
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
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
import org.jraf.android.util.log.LogUtil;
import org.jraf.android.util.log.wrapper.Log;
import org.jraf.android.util.parcelable.ParcelableUtil;

public class NotificationWearableListenerService extends WearableListenerService {
    private static final int NOTIFICATION_ID = 0;
    private WearHelper mWearHelper = WearHelper.get();

    public NotificationWearableListenerService() {}

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
            Log.d("path=" + path); int type = dataEvent.getType(); Log.d("type=" + LogUtil.getConstantName(DataEvent.class, type, "TYPE_"));
            if (type == DataEvent.TYPE_DELETED) {
                dismissNotification();
            } else {
                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap dataMap = dataMapItem.getDataMap();
                String title = dataMap.getString(WearHelper.EXTRA_TITLE); String textShort = dataMap.getString(WearHelper.EXTRA_TEXT_SHORT);
                String textLong = dataMap.getString(WearHelper.EXTRA_TEXT_LONG);
                Asset photoAsset = dataMap.getAsset(WearHelper.EXTRA_PHOTO);
                Bitmap photo = null;
                if (photoAsset != null) {
                    // Blocking
                    mWearHelper.connect(this);
                    photo = mWearHelper.loadBitmapFromAsset(photoAsset);
                }
                byte[] contactUriBytes = dataMap.getByteArray(WearHelper.EXTRA_CONTACT_URI);
                Uri contactUri = ParcelableUtil.unparcel(contactUriBytes, Uri.CREATOR);
                String phoneNumber = dataMap.getString(WearHelper.EXTRA_PHONE_NUMBER);

                showNotification(title, textShort, textLong, photo, contactUri, phoneNumber);
            }
        }
    }

    private void dismissNotification() {
        Log.d();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void showNotification(String title, String textShort, String textLong, @Nullable Bitmap photo, Uri contactUri, @Nullable String phoneNumber) {
        Log.d();
        NotificationCompat.Builder mainNotifBuilder = new NotificationCompat.Builder(this);

        // Small icon
        mainNotifBuilder.setSmallIcon(R.drawable.ic_launcher);

        // Title
        SpannableString spannableTitle = new SpannableString(title);
        Object appearanceSpan = new TextAppearanceSpan(this, R.style.NotificationContentTitleTextAppearance);
        spannableTitle.setSpan(appearanceSpan, 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mainNotifBuilder.setContentTitle(spannableTitle);

        // Text (short) -- This may be completely useless since the "big text style" (below) is always used instead
        SpannableString spannableTextShort = new SpannableString(textShort);
        appearanceSpan = new TextAppearanceSpan(this, R.style.NotificationContentTextTextAppearance);
        spannableTextShort.setSpan(appearanceSpan, 0, textShort.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mainNotifBuilder.setContentText(spannableTextShort);

        // Text (long)
        SpannableString spannableTextLong = new SpannableString(textLong);
        appearanceSpan = new TextAppearanceSpan(this, R.style.NotificationContentTextTextAppearance);
        spannableTextLong.setSpan(appearanceSpan, 0, textLong.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mainNotifBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(spannableTextLong));

        // Dismiss intent
        Intent dismissIntent = new Intent(NotificationIntentService.ACTION_DISMISS_NOTIFICATION, null, this, NotificationIntentService.class);
        PendingIntent dismissPendingIntent = PendingIntent.getService(this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mainNotifBuilder.setDeleteIntent(dismissPendingIntent);

        // Wear specifics
        NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender();

        // Contact photo
        if (photo != null) wearableExtender.setBackground(photo);

        // Actions
        if (phoneNumber != null) {
            // Call action
            Intent callIntent = new Intent(NotificationIntentService.ACTION_CALL, null, this, NotificationIntentService.class);
            callIntent.putExtra(NotificationIntentService.EXTRA_PHONE_NUMBER, phoneNumber);
            PendingIntent callPendingIntent = PendingIntent.getService(this, 0, callIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            String callText = getString(R.string.notification_action_call);
            wearableExtender.addAction(new NotificationCompat.Action(R.drawable.ic_action_call_full, callText, callPendingIntent));

            // Sms action
            Intent smsIntent = new Intent(NotificationIntentService.ACTION_SMS, null, this, NotificationIntentService.class);
            smsIntent.putExtra(NotificationIntentService.EXTRA_PHONE_NUMBER, phoneNumber);
            PendingIntent smsPendingIntent = PendingIntent.getService(this, 0, smsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            String smsText = getString(R.string.notification_action_sms);
            wearableExtender.addAction(new NotificationCompat.Action(R.drawable.ic_action_sms_full, smsText, smsPendingIntent));
        }

        // 'Show contact' action
        Intent showContactIntent = new Intent(NotificationIntentService.ACTION_SHOW_CONTACT, null, this, NotificationIntentService.class);
        showContactIntent.setData(contactUri);
        PendingIntent showContactPendingIntent = PendingIntent.getService(this, 0, showContactIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        String showContactText = getString(R.string.notification_action_showContact);
        wearableExtender.addAction(new NotificationCompat.Action(R.drawable.ic_action_show_contact_full, showContactText, showContactPendingIntent));

        // Misc
        mainNotifBuilder.setPriority(NotificationCompat.PRIORITY_HIGH); // Time sensitive, try to appear on top
        mainNotifBuilder.setCategory(NotificationCompat.CATEGORY_STATUS); // Not sure if this category is really the most appropriate
        wearableExtender.setHintScreenTimeout(NotificationCompat.WearableExtender.SCREEN_TIMEOUT_LONG); // Could be useful

        mainNotifBuilder.extend(wearableExtender);

        // Show the notification
        Notification notification = mainNotifBuilder.build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        mWearHelper.disconnect();
        super.onDestroy();
    }
}
