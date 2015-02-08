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

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import org.jraf.android.dcn.R;
import org.jraf.android.dcn.common.wear.WearHelper;
import org.jraf.android.dcn.handheld.Constants;
import org.jraf.android.dcn.handheld.app.addressinfo.list.AddressInfoLoader;
import org.jraf.android.dcn.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.util.log.wrapper.Log;
import org.jraf.android.util.string.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class GeofencingService extends IntentService {
    public static final String ACTION_REFRESH_GEOFENCES = "ACTION_REFRESH_GEOFENCES";
    public static final String ACTION_DISMISS_NOTIFICATION = "ACTION_DISMISS_NOTIFICATION";

    private static final int NOTIFICATION_RESPONSIVENESS_MS = 5 * 1000; // 4 seconds
    private static final float RADIUS_M = 200;
    private static final int DISMISS_TIMEOUT_MS = 6 * 60 * 1000; // 6 minutes
    private static final int NOTIFICATION_ID = 0;

    private GeofencingHelper mGeofencingHelper = GeofencingHelper.get();
    private WearHelper mWearHelper = WearHelper.get();

    public GeofencingService() {
        super(GeofencingService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("intent=" + StringUtil.toString(intent));
        String action = intent.getAction();
        if (action == null) action = ""; // Avoid null to be able to use switch
        switch (action) {
            case ACTION_REFRESH_GEOFENCES:
                // Triggered by app logic
                mGeofencingHelper.connect(this);  // Blocking

                SharedPreferences preferenceManager = PreferenceManager.getDefaultSharedPreferences(this);
                boolean enabled = preferenceManager.getBoolean(Constants.PREF_GEOFENCING_ENABLED, Constants.PREF_GEOFENCING_ENABLED_DEFAULT);
                if (enabled) {
                    refreshGeofences();
                } else {
                    removeAllGeofences();
                    // Also dismiss any prior notifications
                    dismissNotification();
                }
                break;

            case ACTION_DISMISS_NOTIFICATION:
                // Triggered when dismissing the handheld notification
                dismissNotification();
                break;

            default:
                // Triggered by the geofence system
                GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
                if (geofencingEvent == null) {
                    Log.w("Received a null GeofencingEvent: ignore");
                    return;
                }
                if (geofencingEvent.hasError()) {
                    Log.w("Received an error GeofencingEvent (errorCode=" + geofencingEvent.getErrorCode() + "): ignore");
                    return;
                }
                // Get the corresponding AddressInfo (if several, only consider the first one)
                String geofenceId = geofencingEvent.getTriggeringGeofences().get(0).getRequestId();
                List<AddressInfo> addressInfoList = AddressInfoLoader.retrieveAddressInfoList(this);
                AddressInfo addressInfo = null;
                for (AddressInfo add : addressInfoList) {
                    if (add.uri.toString().equals(geofenceId)) {
                        // Found it
                        addressInfo = add;
                        break;
                    }
                }
                if (addressInfo == null) {
                    // Can happen if the AddressInfo has been deleted, and the geofences were not refreshed (maybe after a
                    // manual contact edit, or a contact sync?)
                    Log.w("The geofence id does not match any AddressInfo: ignore");
                    return;
                }

                switch (geofencingEvent.getGeofenceTransition()) {
                    case Geofence.GEOFENCE_TRANSITION_ENTER:
                        showEnteredNotification(addressInfo);
                        break;

                    case Geofence.GEOFENCE_TRANSITION_EXIT:
                    case Geofence.GEOFENCE_TRANSITION_DWELL:
                        dismissNotification();
                        break;
                }
                break;
        }
    }

    private void refreshGeofences() {
        Log.d();
        // First remove all geofences
        removeAllGeofences();

        // Add the fresh list of geofences
        List<AddressInfo> addressInfoList = AddressInfoLoader.retrieveAddressInfoList(this);
        List<Geofence> geofenceList = new ArrayList<>(addressInfoList.size());
        for (AddressInfo addressInfo : addressInfoList) {
            geofenceList.add(toGeofence(addressInfo));
        }
        mGeofencingHelper.addGeofences(geofenceList);
    }

    private Geofence toGeofence(AddressInfo addressInfo) {
        Geofence.Builder geofenceBuilder = new Geofence.Builder();
        geofenceBuilder.setRequestId(addressInfo.uri.toString());
        geofenceBuilder.setExpirationDuration(Geofence.NEVER_EXPIRE);
        geofenceBuilder.setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT |
                Geofence.GEOFENCE_TRANSITION_DWELL);
        geofenceBuilder.setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS);
        geofenceBuilder.setCircularRegion(addressInfo.latitude, addressInfo.longitude, RADIUS_M);
        geofenceBuilder.setLoiteringDelay(DISMISS_TIMEOUT_MS);
        return geofenceBuilder.build();
    }

    private void removeAllGeofences() {
        Log.d();
        mGeofencingHelper.removeAllGeofences();
    }


    /*
     * Notification.
     */

    private void showEnteredNotification(AddressInfo addressInfo) {
        Log.d("addressInfo=" + addressInfo);
        NotificationCompat.Builder mainNotifBuilder = new NotificationCompat.Builder(this);
        mainNotifBuilder.setSmallIcon(R.drawable.ic_stat_entered);
        String title = getNotificationTitle(addressInfo);

        String textSmall = getNotificationText(addressInfo, false);
        String textBig = getNotificationText(addressInfo, true);

        // Make a bigger title
        SpannableString titleSpannable = new SpannableString(title);
        Object span = new TextAppearanceSpan(this, R.style.NotificationContentTitleTextAppearance);
        titleSpannable.setSpan(span, 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mainNotifBuilder.setContentTitle(titleSpannable);

        mainNotifBuilder.setTicker(title);
        mainNotifBuilder.setContentText(textSmall);
        mainNotifBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(textBig));
        mainNotifBuilder.setPriority(NotificationCompat.PRIORITY_HIGH); // Time sensitive, try to appear on top
        mainNotifBuilder.setCategory(NotificationCompat.CATEGORY_STATUS); // Not sure if this category is really the most appropriate
        mainNotifBuilder.setLights(0, 0, 0); // No light
        mainNotifBuilder.setShowWhen(false); // No date
        mainNotifBuilder.addPerson(addressInfo.contactInfo.contentLookupUri.toString());
        // Contact photo
        Bitmap contactPhoto = addressInfo.getContactPhoto(this);
        if (contactPhoto != null) mainNotifBuilder.setLargeIcon(contactPhoto);

        // Auto cancel
        mainNotifBuilder.setAutoCancel(true);

        // Dismiss intent
        Intent dismissIntent = new Intent(ACTION_DISMISS_NOTIFICATION, null, this, getClass());
        PendingIntent dismissPendingIntent = PendingIntent.getService(this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mainNotifBuilder.setDeleteIntent(dismissPendingIntent);

        // Main action (click on the notification itself)
        Intent mainIntent = new Intent(Intent.ACTION_VIEW);
        mainIntent.setData(addressInfo.contactInfo.contentLookupUri);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mainNotifBuilder.setContentIntent(mainPendingIntent);

        String phoneNumber = addressInfo.getContactPhoneNumber(this);
        if (phoneNumber != null) {
            // Call action
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            PendingIntent callPendingIntent = PendingIntent.getActivity(this, 0, callIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            String callText = getString(R.string.notification_action_call);
            mainNotifBuilder.addAction(R.drawable.ic_action_call, callText, callPendingIntent);

            // Sms action
            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setData(Uri.parse("sms:" + phoneNumber));
            smsIntent.putExtra("sms_body", getString(R.string.notification_action_sms_body));
            PendingIntent smsPendingIntent = PendingIntent.getActivity(this, 0, smsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            String smsText = getString(R.string.notification_action_sms);
            mainNotifBuilder.addAction(R.drawable.ic_action_sms, smsText, smsPendingIntent);
        }

        // Since we have a specific Wear notification, show this one only on handheld
        mainNotifBuilder.setLocalOnly(true);

        // Show it
        Notification notification = mainNotifBuilder.build();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);

        // Show a Wear notification
        // Blocking
        mWearHelper.connect(this);
        mWearHelper.putNotification(title, textSmall, contactPhoto, addressInfo.contactInfo.contentLookupUri, phoneNumber);
    }

    private String getNotificationTitle(AddressInfo addressInfo) {
        if (addressInfo.codeList.isEmpty()) {
            if (addressInfo.otherInfo == null) {
                return addressInfo.contactInfo.displayName;
            }
            return addressInfo.otherInfo;
        }
        String res = "";
        int i = 0;
        for (String code : addressInfo.codeList) {
            if (i > 0) res += " ‒ ";
            res += code;
            i++;
        }

        return res;
    }

    private String getNotificationText(AddressInfo addressInfo, boolean big) {
        if (addressInfo.codeList.isEmpty()) {
            if (addressInfo.otherInfo == null) {
                return null;
            }
            return addressInfo.contactInfo.displayName;
        }
        if (addressInfo.otherInfo == null) {
            return addressInfo.contactInfo.displayName;
        }
        String separator = big ? "\n" : " — ";
        return addressInfo.otherInfo + separator + addressInfo.contactInfo.displayName;
    }

    private void dismissNotification() {
        Log.d();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);

        // Dismiss Wear notification
        // Blocking
        mWearHelper.connect(this);
        mWearHelper.removeNotification();
    }

    public static void refresh(Context context) {
        Log.d();
        Intent intent = new Intent(context, GeofencingService.class);
        intent.setAction(ACTION_REFRESH_GEOFENCES);
        context.startService(intent);
    }

    @Override
    public void onDestroy() {
        mGeofencingHelper.disconnect();
        mWearHelper.disconnect();
        super.onDestroy();
    }
}
