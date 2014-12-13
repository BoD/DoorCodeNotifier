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

    public AddressInfoLoader(Context ctx) {
        super(ctx);
    }

    @Override
    public List<AddressInfo> loadInBackground() {
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
        Cursor c = getContext().getContentResolver().query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArg, sortOrder);
        try {
            while (c.moveToNext()) {
                Uri structuredPostalUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, c.getLong(2));

                AddressInfo addressInfo;
                try {
                    addressInfo = AddressInfo.parse(c.getString(3));
                } catch (ParseException e) {
                    Log.w("Ignoring StructuredPostal " + structuredPostalUri, e);
                    continue;
                }

                addressInfo.uri = structuredPostalUri;
                addressInfo.contactInfo.uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.getLong(0));
                addressInfo.contactInfo.displayName = c.getString(1);

                data.add(addressInfo);
            }
        } finally {
            if (c != null) c.close();
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

//        // Begin monitoring the underlying data source.
//        if (mObserver == null) {
//            mObserver = new SampleObserver();
//            // TODO: register the observer
//        }

        if (takeContentChanged() || mData == null) {
            forceLoad();
        }
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

//        // The Loader is being reset, so we should stop monitoring for changes.
//        if (mObserver != null) {
//            // TODO: unregister the observer
//            mObserver = null;
//        }
    }

    @Override
    public void onCanceled(List<AddressInfo> data) {
        super.onCanceled(data);
        releaseResources(data);
    }

    private void releaseResources(List<AddressInfo> data) {
    }


    // NOTE: Implementing an observer is outside the scope of this post (this example
    // uses a made-up "SampleObserver" to illustrate when/where the observer should
    // be initialized).

    // The observer could be anything so long as it is able to detect content changes
    // and report them to the loader with a call to onContentChanged(). For example,
    // if you were writing a Loader which loads a list of all installed applications
    // on the device, the observer could be a BroadcastReceiver that listens for the
    // ACTION_PACKAGE_ADDED intent, and calls onContentChanged() on the particular
    // Loader whenever the receiver detects that a new application has been installed.
    // Please don’t hesitate to leave a comment if you still find this confusing! :)
//    private SampleObserver mObserver;
}
