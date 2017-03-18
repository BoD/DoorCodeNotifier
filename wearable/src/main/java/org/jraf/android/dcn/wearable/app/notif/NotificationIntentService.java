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

import android.app.IntentService;
import android.content.Intent;
import android.support.wearable.activity.ConfirmationActivity;

import org.jraf.android.dcn.R;
import org.jraf.android.dcn.common.wear.WearHelper;
import org.jraf.android.util.log.Log;
import org.jraf.android.util.string.StringUtil;

public class NotificationIntentService extends IntentService {
    public static final String ACTION_DISMISS_NOTIFICATION = "ACTION_DISMISS_NOTIFICATION";
    public static final String ACTION_SHOW_CONTACT = "ACTION_SHOW_CONTACT";
    public static final String ACTION_CALL = "ACTION_CALL";
    public static final String ACTION_SMS = "ACTION_SMS";

    public static final String EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER";

    private WearHelper mWearHelper = WearHelper.get();

    public NotificationIntentService() {
        super(NotificationIntentService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("intent=" + StringUtil.toString(intent));

        // Blocking
        mWearHelper.connect(this);

        String action = intent.getAction();
        switch (action) {
            case ACTION_DISMISS_NOTIFICATION:
                // Triggered when dismissing the Wear notification
                mWearHelper.removeNotification();
                break;

            case ACTION_SHOW_CONTACT:
                mWearHelper.sendMessageShowContact(intent.getData());
                showOpenOnPhoneAnimation();
                break;

            case ACTION_CALL:
                mWearHelper.sendMessageCall(intent.getStringExtra(EXTRA_PHONE_NUMBER));
                showOpenOnPhoneAnimation();
                break;

            case ACTION_SMS:
                mWearHelper.sendMessageSms(intent.getStringExtra(EXTRA_PHONE_NUMBER));
                showOpenOnPhoneAnimation();
                break;
        }
    }

    private void showOpenOnPhoneAnimation() {
        Intent intent = new Intent(this, ConfirmationActivity.class);
        intent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.OPEN_ON_PHONE_ANIMATION);
        intent.putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.notification_openedOnPhone));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        mWearHelper.disconnect();
        super.onDestroy();
    }

}
