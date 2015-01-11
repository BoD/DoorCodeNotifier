package org.jraf.android.digibod.handheld.app.addressinfo.list;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.content.AsyncTaskLoader;

import org.jraf.android.digibod.handheld.app.geofencing.GeofencingService;
import org.jraf.android.digibod.handheld.model.addressinfo.AddressInfo;
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
        addressInfo.contactInfo.displayName = cursor.getString(1);
        return addressInfo;
    }

    private static Cursor queryContactProvider(Context context) {
        String[] projection = {
                ContactsContract.Data.CONTACT_ID, // 0
                ContactsContract.Contacts.DISPLAY_NAME, // 1
                ContactsContract.Data._ID, // 2
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, // 3
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
        while (cursor.moveToNext()) {
            AddressInfo addressInfo = getAddressInfoFromCursor(cursor);
            if (addressInfo == null) continue;

            data.add(addressInfo);
        }
        cursor.close();

        return data;
    }

    @Override
    public void deliverResult(List<AddressInfo> data) {
        if (isReset()) {
            releaseResources(data);
            return;
        }

        List<AddressInfo> oldData = mData;
        mData = data;

        if (isStarted()) {
            super.deliverResult(data);
        }

        if (oldData != null && oldData != data) {
            releaseResources(oldData);
        }
    }

    @Override
    protected void onStartLoading() {
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
        Intent intent = new Intent(getContext(), GeofencingService.class);
        intent.setAction(GeofencingService.ACTION_REFRESH_GEOFENCES);
        getContext().startService(intent);
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    protected void onReset() {
        onStopLoading();

        if (mData != null) {
            releaseResources(mData);
            mData = null;
        }
    }

    @Override
    public void onCanceled(List<AddressInfo> data) {
        super.onCanceled(data);
        releaseResources(data);
    }

    private void releaseResources(List<AddressInfo> data) {
        data.clear();
        if (mCursor != null) mCursor.close();
    }
}
