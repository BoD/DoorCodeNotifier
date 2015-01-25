package org.jraf.android.digibod.handheld.app.geofencing;

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

import org.jraf.android.digibod.R;
import org.jraf.android.digibod.handheld.Constants;
import org.jraf.android.digibod.handheld.app.addressinfo.list.AddressInfoLoader;
import org.jraf.android.digibod.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.util.log.wrapper.Log;
import org.jraf.android.util.string.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class GeofencingService extends IntentService {
    public static final String ACTION_REFRESH_GEOFENCES = "ACTION_REFRESH_GEOFENCES";
    private static final int NOTIFICATION_RESPONSIVENESS_MS = 5 * 1000; // 4 seconds
    private static final float RADIUS_M = 200;
    private static final int DISMISS_TIMEOUT_MS = 6 * 60 * 1000; // 6 minutes
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
                    showEnteredNotification(addressInfo);
                    break;

                case Geofence.GEOFENCE_TRANSITION_EXIT:
                case Geofence.GEOFENCE_TRANSITION_DWELL:
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
                Geofence.GEOFENCE_TRANSITION_DWELL);
        geofenceBuilder.setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS);
        geofenceBuilder.setCircularRegion(addressInfo.latitude, addressInfo.longitude, RADIUS_M);
        geofenceBuilder.setLoiteringDelay(DISMISS_TIMEOUT_MS);
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

//        // Go to the AddressInfo's edit activity
//        TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(this);
//        taskStackBuilder.addParentStack(AddressInfoListActivity.class);
//        Intent intent = new Intent(this, AddressInfoEditActivity.class).setData(addressInfo.uri);
//        taskStackBuilder.addNextIntent(intent);
//        mainNotifBuilder.setContentIntent(taskStackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));

        // Auto cancel
        mainNotifBuilder.setAutoCancel(true);

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

            // Sms action
            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setData(Uri.parse("sms:" + phoneNumber));
            smsIntent.putExtra("sms_body", getString(R.string.notification_action_sms_body));
            PendingIntent smsPendingIntent = PendingIntent.getActivity(this, 0, smsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            String smsText = getString(R.string.notification_action_sms);

            // Handheld
            mainNotifBuilder.addAction(R.drawable.ic_action_call, callText, callPendingIntent);
            mainNotifBuilder.addAction(R.drawable.ic_action_sms, smsText, smsPendingIntent);

            // Wearable (we need to do that to have specific 'full' icons on wearables)
            NotificationCompat.WearableExtender wearableExtender = new NotificationCompat.WearableExtender();
            wearableExtender.addAction(new NotificationCompat.Action(R.drawable.ic_action_call_full, callText, callPendingIntent));
            wearableExtender.addAction(new NotificationCompat.Action(R.drawable.ic_action_sms_full, smsText, smsPendingIntent));

            // Could be useful
            wearableExtender.setHintScreenTimeout(NotificationCompat.WearableExtender.SCREEN_TIMEOUT_LONG);

            mainNotifBuilder.extend(wearableExtender);
        }

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
