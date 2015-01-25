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

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SwitchCompat;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import org.jraf.android.dcn.R;
import org.jraf.android.dcn.handheld.Constants;
import org.jraf.android.dcn.handheld.app.addressinfo.edit.AddressInfoEditActivity;
import org.jraf.android.dcn.handheld.app.geofencing.GeofencingService;
import org.jraf.android.dcn.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.util.async.Task;
import org.jraf.android.util.async.TaskFragment;
import org.jraf.android.util.dialog.AlertDialogFragment;
import org.jraf.android.util.dialog.AlertDialogListener;
import org.jraf.android.util.log.wrapper.Log;
import org.jraf.android.util.string.StringUtil;

import java.util.ArrayList;

import butterknife.ButterKnife;
import butterknife.OnClick;


public class AddressInfoListActivity extends ActionBarActivity implements AlertDialogListener, AddressInfoCallbacks {
    private static final int REQUEST_CONTACT_PICK = 0;
    private static final int DIALOG_CHOOSE_ADDRESS_TO_EDIT = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.addressinfo_list);

        // Custom action bar that contains the "done" button for saving changes
        ActionBar actionBar = getSupportActionBar();
        @SuppressLint("InflateParams") View customActionBarView = getLayoutInflater().inflate(R.layout.addressinfo_list_actionbar, null);
        SwitchCompat swiGeotracking = (SwitchCompat) customActionBarView.findViewById(R.id.swiGeofencing);
        SharedPreferences preferenceManager = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = preferenceManager.getBoolean(Constants.PREF_GEOFENCING_ENABLED, Constants.PREF_GEOFENCING_ENABLED_DEFAULT);
        swiGeotracking.setChecked(enabled);
        swiGeotracking.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onGeotrackingCheckedChanged(isChecked);
            }
        });
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(customActionBarView, new ActionBar.LayoutParams(Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        ButterKnife.inject(this);

        // TODO Check for Google Play Services ( http://developer.android.com/training/location/geofencing.html )
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.addressinfo_list, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
//            case R.id.action_add:
//                onAddClicked();
//                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @OnClick(R.id.btnAdd)
    protected void onAddClicked() {
        // For some unknown reason, when starting the contact list activity with ACTION_PICK, the "create new contact" button
        // is hidden.  It is visible when starting with ACTION_GET_CONTENT.
        // Because of this, we first find which component deals with ACTION_PICK, but then start it with ACTION_GET_CONTENT.
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        ResolveInfo resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

        intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        // Explicitly set the component found above
        intent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
        intent.putExtra("finishActivityOnSaveCompleted", true); // see http://developer.android.com/training/contacts-provider/modify-data.html

        startActivityForResult(intent, REQUEST_CONTACT_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONTACT_PICK:
                if (resultCode != RESULT_OK) break;
                Log.d("data=" + StringUtil.toString(data));
                Uri contactLookupUri = data.getData();
                checkForMultipleAddresses(contactLookupUri);
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private static class StructuredPostal {
        Uri uri;
        String formattedAddress;
    }

    private void checkForMultipleAddresses(final Uri contactLookupUri) {
        Log.d();
        new TaskFragment(new Task<AddressInfoListActivity>() {
            @Override
            protected void doInBackground() throws Throwable {
                // 1/ Find the contact id from the contact lookup uri
                String[] projection = {ContactsContract.Contacts._ID};
                AddressInfoListActivity a = getActivity();
                Cursor c = a.getContentResolver().query(contactLookupUri, projection, null, null, null);
                long contactId = 0;
                try {
                    if (c.moveToNext()) {
                        contactId = c.getLong(0);
                    } else {
                        // TODO Handle error case
                    }
                } finally {
                    if (c != null) c.close();
                }

                // 2/ Find addresses for this contact
                ArrayList<StructuredPostal> structuredPostalList = new ArrayList<>();
                projection = new String[] {
                        ContactsContract.Data._ID, // 0
                        ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, // 1
                };
                String selection = ContactsContract.Contacts.Data.MIMETYPE + "=? AND " + ContactsContract.Data.CONTACT_ID + "=?";
                String[] selectionArg = {ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, String.valueOf(contactId)};
                c = a.getContentResolver().query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArg, null);
                try {
                    while (c.moveToNext()) {
                        StructuredPostal structuredPostal = new StructuredPostal();
                        structuredPostal.uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, c.getLong(0));
                        structuredPostal.formattedAddress = c.getString(1);
                        structuredPostalList.add(structuredPostal);
                    }
                } finally {
                    if (c != null) c.close();
                }

                if (structuredPostalList.isEmpty()) {
                    // The selected contact has no addresses: create one now and edit it
                    a.createAddressAndEdit(contactId);
                } else if (structuredPostalList.size() == 1) {
                    // The selected contact has 1 address
                    StructuredPostal structuredPostal = structuredPostalList.get(0);
                    if (AddressInfo.isAugmented(structuredPostal.formattedAddress)) {
                        // The address is already augmented: create a new address now and edit it
                        a.createAddressAndEdit(contactId);
                    } else {
                        // The address is not augmented: edit it now
                        a.startAddressInfoEditActivity(structuredPostal.uri);
                    }
                } else {
                    // The selected contact has several addresses: ask the user which one they want to edit
                    a.chooseAddressToEdit(contactId, structuredPostalList);
                }
            }
        }).execute(getSupportFragmentManager());
    }

    private void startAddressInfoEditActivity(Uri uri) {
        Intent intent = new Intent(this, AddressInfoEditActivity.class);
        intent.setData(uri);
        this.startActivity(intent);
    }

    private void createAddressAndEdit(long contactId) {
        Log.d("contactId=" + contactId);

        // We need a raw contact for the selected contact.
        // If there are several, just arbitrarily pick the first one - this is not ideal, but the alternative would be to ask the user
        // which is not cool either.
        String[] projection = {ContactsContract.RawContacts._ID};
        String selection = ContactsContract.RawContacts.CONTACT_ID + "=?";
        String[] selectionArgs = {String.valueOf(contactId)};
        Cursor c = getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI, projection, selection, selectionArgs, null);
        long rawContactId = -1;
        try {
            if (c.moveToFirst()) {
                rawContactId = c.getLong(0);
            } else {
                // Should never happen
                Log.e("Could not find raw contact for contact " + contactId, new Exception());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AddressInfoListActivity.this, R.string.common_unexpected, Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
        } finally {
            c.close();
        }

        ContentValues contentValues = new ContentValues(2);
        contentValues.put(ContactsContract.Contacts.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);
        contentValues.put(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, "");
        contentValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        Uri res = getContentResolver().insert(ContactsContract.Data.CONTENT_URI, contentValues);
        Log.d("res=" + res);
        startAddressInfoEditActivity(res);
    }

    private void chooseAddressToEdit(long contactId, ArrayList<StructuredPostal> structuredPostalList) {
        Log.d();
        ArrayList<CharSequence> items = new ArrayList<>(structuredPostalList.size());
        Bundle payload = new Bundle();
        payload.putLong("contactId", contactId);
        ArrayList<Parcelable> uris = new ArrayList<>(structuredPostalList.size());
        for (StructuredPostal structuredPostal : structuredPostalList) {
            if (AddressInfo.isAugmented(structuredPostal.formattedAddress)) {
                // Skip addresses that are already augmented
                continue;
            }
            items.add(structuredPostal.formattedAddress);
            uris.add(structuredPostal.uri);
        }

        if (uris.isEmpty()) {
            // After skipping augmented addresses, we may have none left.
            // In this case, create one now and edit it.
            createAddressAndEdit(contactId);
            return;
        } else if (uris.size() == 1) {
            // After skipping augmented addresses, we only have one left: edit it now
            startAddressInfoEditActivity((Uri) uris.get(0));
            return;
        }

        // Ask the user which address they want to edit
        AlertDialogFragment dialogFragment = AlertDialogFragment.newInstance(DIALOG_CHOOSE_ADDRESS_TO_EDIT);
        dialogFragment.setItems(items);
        payload.putParcelableArrayList("uris", uris);
        dialogFragment.setTitle(R.string.addressInfo_list_chooseAddress);
        dialogFragment.setPayload(payload);
        dialogFragment.show(getSupportFragmentManager());
    }


    /*
     * AlertDialogListener implementation.
     */

    @Override
    public void onClickPositive(int tag, Object payload) {
    }

    @Override
    public void onClickNegative(int tag, Object payload) {
    }

    @Override
    public void onClickListItem(int tag, int index, Object payload) {
        switch (tag) {
            case DIALOG_CHOOSE_ADDRESS_TO_EDIT:
                Bundle payloadBundle = (Bundle) payload;
                ArrayList<Parcelable> uris = payloadBundle.getParcelableArrayList("uris");
                long contactId = payloadBundle.getLong("contactId");
                Uri chosenUri = (Uri) uris.get(index);
                Log.d("contactId=" + contactId + " chosenUri=" + chosenUri);
                startAddressInfoEditActivity(chosenUri);
                break;
        }
    }


    /*
     * AddressInfoCallbacks implementation.
     */

    @Override
    public void onAddressInfoClicked(AddressInfo addressInfo) {
        Log.d("addressInfo=" + addressInfo.uri);
        startAddressInfoEditActivity(addressInfo.uri);
    }


    private void onGeotrackingCheckedChanged(boolean isChecked) {
        SharedPreferences preferenceManager = PreferenceManager.getDefaultSharedPreferences(this);
        preferenceManager.edit().putBoolean(Constants.PREF_GEOFENCING_ENABLED, isChecked).commit();
        GeofencingService.refresh(this);
        Toast.makeText(this, isChecked ? R.string.addressInfo_list_geotrackingOn : R.string.addressInfo_list_geotrackingOff, Toast.LENGTH_SHORT).show();
    }

}
