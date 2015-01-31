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
package org.jraf.android.dcn.handheld.app.addressinfo.list;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.content.AsyncTaskLoader;

import org.jraf.android.dcn.handheld.app.geofencing.GeofencingService;
import org.jraf.android.dcn.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.util.log.wrapper.Log;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class AddressInfoLoader extends AsyncTaskLoader<List<AddressInfo>> {
    private List<AddressInfo> mData;
    private ForceLoadContentObserver mObserver = new ForceLoadContentObserver();
    private Cursor mCursor;

    public AddressInfoLoader(Context ctx) {
        super(ctx);
    }

    @Override
    public List<AddressInfo> loadInBackground() {
        Log.d();
        List<AddressInfo> data = new ArrayList<>();

        if (mCursor != null && !mCursor.isClosed()) mCursor.close();
        mCursor = queryContactProvider(getContext());
        mCursor.registerContentObserver(mObserver);
        while (mCursor.moveToNext()) {
            AddressInfo addressInfo = getAddressInfoFromCursor(mCursor);
            if (addressInfo == null) continue;

            data.add(addressInfo);
        }

        return data;
    }

    private static AddressInfo getAddressInfoFromCursor(Cursor cursor) {
        Uri structuredPostalUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, cursor.getLong(2));

        AddressInfo addressInfo;
        try {
            addressInfo = AddressInfo.parseAugmented(cursor.getString(3));
        } catch (ParseException e) {
            Log.w("Ignoring StructuredPostal " + structuredPostalUri, e);
            return null;
        }

        addressInfo.uri = structuredPostalUri;
        addressInfo.contactInfo.uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cursor.getLong(0));
        addressInfo.contactInfo.contentLookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, cursor.getString(4));
        addressInfo.contactInfo.displayName = cursor.getString(1);
        return addressInfo;
    }

    private static Cursor queryContactProvider(Context context) {
        String[] projection = {
                ContactsContract.Data.CONTACT_ID, // 0
                ContactsContract.Contacts.DISPLAY_NAME, // 1
                ContactsContract.Data._ID, // 2
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, // 3
                ContactsContract.Contacts.LOOKUP_KEY, // 4
        };
        String selection =
                ContactsContract.Contacts.Data.MIMETYPE + "=? AND " + ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS + " LIKE ?";
        String[] selectionArg = {ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, "%" + AddressInfo.SEPARATOR + "%"};
        String sortOrder = ContactsContract.Contacts.DISPLAY_NAME;
        return context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArg, sortOrder);
    }

    public static List<AddressInfo> retrieveAddressInfoList(Context context) {
        Log.d();
        List<AddressInfo> data = new ArrayList<>();

        Cursor cursor = queryContactProvider(context);
        try {
            while (cursor.moveToNext()) {
                AddressInfo addressInfo = getAddressInfoFromCursor(cursor);
                if (addressInfo == null) continue;

                data.add(addressInfo);
            }
        } finally {
            cursor.close();
        }

        return data;
    }

    @Override
    public void deliverResult(List<AddressInfo> data) {
        Log.d();
        mData = data;

        if (isStarted()) {
            super.deliverResult(data);
        }
    }

    @Override
    protected void onStartLoading() {
        Log.d();
        if (mData != null) {
            deliverResult(mData);
        }

        if (takeContentChanged() || mData == null) {
            forceLoad();
        }
    }

    @Override
    public void onContentChanged() {
        Log.d();
        super.onContentChanged();
        GeofencingService.refresh(getContext());
    }

    @Override
    protected void onStopLoading() {
        Log.d();
        cancelLoad();
    }

    @Override
    protected void onReset() {
        Log.d();
        onStopLoading();
        releaseResources();
    }

    @Override
    public void onCanceled(List<AddressInfo> data) {
        Log.d();
        super.onCanceled(data);
        releaseResources();
    }

    private void releaseResources() {
        Log.d();
        mData = null;
        if (mCursor != null) {
            if (!mCursor.isClosed()) mCursor.close();
            mCursor = null;
        }
    }
}
