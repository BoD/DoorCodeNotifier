package org.jraf.android.digibod.handheld.app.geofencing;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import org.jraf.android.digibod.R;
import org.jraf.android.digibod.handheld.Constants;
import org.jraf.android.digibod.handheld.app.addressinfo.list.AddressInfoLoader;
import org.jraf.android.digibod.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.util.annotation.Background;
import org.jraf.android.util.log.wrapper.Log;
import org.jraf.android.util.string.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class GeofencingService extends IntentService {
    public static final String ACTION_REFRESH_GEOFENCES = "ACTION_REFRESH_GEOFENCES";
    private static final int NOTIFICATION_RESPONSIVENESS_MS = 5 * 1000;
    private static final float RADIUS_M = 200;
    private static final int NOTIFICATION_ID = 1;

    public GeofencingService() {
        super(GeofencingService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("intent=" + StringUtil.toString(intent));
        if (ACTION_REFRESH_GEOFENCES.equals(intent.getAction())) {
            // Triggered by app logic
            GeofencingHelper.get().connect(this);
            SharedPreferences preferenceManager = PreferenceManager.getDefaultSharedPreferences(this);
            boolean enabled = preferenceManager.getBoolean(Constants.PREF_GEOFENCING_ENABLED, Constants.PREF_GEOFENCING_ENABLED_DEFAULT);
            if (enabled) {
                refreshGeofences();
            } else {
                removeAllGeofences();
                // Also dismiss any prior notifications
                dismissNotification();
            }
        } else {
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
                // Can happen if the AddressInfo has been deleted, and the geofences were not refreshed (maybe after a contact sync?)
                Log.w("The geofence id does not match any AddressInfo: ignore");
                return;
            }

            switch (geofencingEvent.getGeofenceTransition()) {
                case Geofence.GEOFENCE_TRANSITION_ENTER:
                case Geofence.GEOFENCE_TRANSITION_DWELL:
                    showEnteredNotification(addressInfo);
                    break;

                case Geofence.GEOFENCE_TRANSITION_EXIT:
                    dismissNotification();
                    break;
            }
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
        GeofencingHelper.get().addGeofences(geofenceList);
    }

    private Geofence toGeofence(AddressInfo addressInfo) {
        Geofence.Builder geofenceBuilder = new Geofence.Builder();
        geofenceBuilder.setRequestId(addressInfo.uri.toString());
        geofenceBuilder.setExpirationDuration(Geofence.NEVER_EXPIRE);
        geofenceBuilder.setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT |
                Geofence.GEOFENCE_TRANSITION_DWELL); // TODO remove this because we don't really want dwell
        geofenceBuilder.setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS);
        geofenceBuilder.setCircularRegion(addressInfo.latitude, addressInfo.longitude, RADIUS_M);
        geofenceBuilder.setLoiteringDelay(1000); // TODO remove this because we don't really want dwell
        return geofenceBuilder.build();
    }

    private void removeAllGeofences() {
        Log.d();
        GeofencingHelper.get().removeAllGeofences();
    }


    /*
     * Notification.
     */

    private void showEnteredNotification(AddressInfo addressInfo) {
        Log.d("addressInfo=" + addressInfo);
        NotificationCompat.Builder mainNotifBuilder = new NotificationCompat.Builder(this);
        mainNotifBuilder.setSmallIcon(R.drawable.ic_stat_entered); // TODO a real icon
        String title = getNotificationTitle(addressInfo);
        String textSmall = getNotificationText(addressInfo, false);
        String textBig = getNotificationText(addressInfo, true);
        mainNotifBuilder.setContentTitle(title);
        mainNotifBuilder.setTicker(title);
        mainNotifBuilder.setContentText(textSmall);
        mainNotifBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(textBig));
        mainNotifBuilder.setPriority(NotificationCompat.PRIORITY_HIGH); // Time sensitive, try to appear on top
        mainNotifBuilder.setCategory(NotificationCompat.CATEGORY_STATUS); // Not sure if this category is really the most appropriate
        mainNotifBuilder.setLights(0, 0, 0); // No light
        mainNotifBuilder.setShowWhen(false); // No date
        mainNotifBuilder.addPerson(addressInfo.contactInfo.contentLookupUri.toString());
        // Contact photo
        Bitmap contactPhoto = getContactPhoto(addressInfo.contactInfo.uri);
        if (contactPhoto != null) mainNotifBuilder.setLargeIcon(contactPhoto);

//        // Go to the AddressInfo's edit activity
//        TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(this);
//        taskStackBuilder.addParentStack(AddressInfoListActivity.class);
//        Intent intent = new Intent(this, AddressInfoEditActivity.class).setData(addressInfo.uri);
//        taskStackBuilder.addNextIntent(intent);
//        mainNotifBuilder.setContentIntent(taskStackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
        mainNotifBuilder.setAutoCancel(true);

        Notification notification = mainNotifBuilder.build();

        // Default vibration
        notification.defaults |= Notification.DEFAULT_VIBRATE; // TODO remove

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
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

    @Nullable
    @Background(Background.Type.DISK)
    private Bitmap getContactPhoto(Uri contactUri) {
        Uri photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
        String[] projection = {ContactsContract.Contacts.Photo.PHOTO};
        Cursor cursor = getContentResolver().query(photoUri, projection, null, null, null);
        if (cursor == null) return null;
        try {
            if (cursor.moveToFirst()) {
                byte[] data = cursor.getBlob(0);
                if (data != null) return BitmapFactory.decodeByteArray(data, 0, data.length);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private void dismissNotification() {
        Log.d();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    public static void refresh(Context context) {
        Log.d();
        Intent intent = new Intent(context, GeofencingService.class);
        intent.setAction(ACTION_REFRESH_GEOFENCES);
        context.startService(intent);
    }
}
