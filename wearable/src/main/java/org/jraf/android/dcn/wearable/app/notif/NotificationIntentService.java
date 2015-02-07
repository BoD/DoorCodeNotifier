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

import org.jraf.android.dcn.common.wear.WearHelper;
import org.jraf.android.util.log.wrapper.Log;
import org.jraf.android.util.string.StringUtil;

public class NotificationIntentService extends IntentService {
    public static final String ACTION_DISMISS_NOTIFICATION = "ACTION_DISMISS_NOTIFICATION";

    private WearHelper mWearHelper = WearHelper.get();

    public NotificationIntentService() {
        super(NotificationIntentService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("intent=" + StringUtil.toString(intent)); String action = intent.getAction(); if (ACTION_DISMISS_NOTIFICATION.equals(action)) {
            // Triggered when dismissing the Wear notification
            dismissNotification();
        }
    }


    private void dismissNotification() {
        Log.d();

        // Dismiss Wear notification
        // Blocking
        mWearHelper.connect(this); mWearHelper.removeNotification();
    }

    @Override
    public void onDestroy() {
        mWearHelper.disconnect(); super.onDestroy();
    }
}
