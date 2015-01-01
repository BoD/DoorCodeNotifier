package org.jraf.android.digibod.handheld.app.addressinfo.list;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.content.AsyncTaskLoader;

import org.jraf.android.digibod.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.util.log.wrapper.Log;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class AddressInfoLoader extends AsyncTaskLoader<List<AddressInfo>> {
    private List<AddressInfo> mData;
    private ForceLoadContentObserver mObserver;
    private Cursor mCursor;

    public AddressInfoLoader(Context ctx) {
        super(ctx);
        mObserver = new ForceLoadContentObserver();
    }

    @Override
    public List<AddressInfo> loadInBackground() {
        Log.d();
        List<AddressInfo> data = new ArrayList<>();

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
        mCursor = getContext().getContentResolver().query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArg, sortOrder);
        mCursor.registerContentObserver(mObserver);
        while (mCursor.moveToNext()) {
            Uri structuredPostalUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, mCursor.getLong(2));

            AddressInfo addressInfo;
            try {
                addressInfo = AddressInfo.parse(mCursor.getString(3));
            } catch (ParseException e) {
                Log.w("Ignoring StructuredPostal " + structuredPostalUri, e);
                continue;
            }

            addressInfo.uri = structuredPostalUri;
            addressInfo.contactInfo.uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, mCursor.getLong(0));
            addressInfo.contactInfo.displayName = mCursor.getString(1);

            data.add(addressInfo);
        }

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
