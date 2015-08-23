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

import java.util.ArrayList;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.jraf.android.dcn.BuildConfig;
import org.jraf.android.dcn.R;
import org.jraf.android.dcn.handheld.Constants;
import org.jraf.android.dcn.handheld.app.addressinfo.edit.AddressInfoEditActivity;
import org.jraf.android.dcn.handheld.app.geofencing.GeofencingService;
import org.jraf.android.dcn.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.dcn.handheld.util.picasso.location.LocationUtil;
import org.jraf.android.util.about.AboutActivityIntentBuilder;
import org.jraf.android.util.async.Task;
import org.jraf.android.util.async.TaskFragment;
import org.jraf.android.util.dialog.AlertDialogFragment;
import org.jraf.android.util.dialog.AlertDialogListener;
import org.jraf.android.util.log.wrapper.Log;
import org.jraf.android.util.string.StringUtil;
import org.jraf.android.util.ui.UiUtil;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class AddressInfoListActivity extends AppCompatActivity implements AlertDialogListener, AddressInfoCallbacks {
    private static final int REQUEST_CONTACT_PICK = 0;
    private static final int REQUEST_INSTALL_PLAY_SERVICES = 1;

    private static final int DIALOG_CHOOSE_ADDRESS_TO_EDIT = 0;
    private static final int DIALOG_LOCATION_SETTINGS = 1;

    @Bind(R.id.conFencingDisabled)
    protected View mConGeofencingDisabled;

    @Bind(R.id.imgArrowUp)
    protected View mImgArrowUp;

    private SwitchCompat mSwiGeofencing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.addressinfo_list);
        ButterKnife.bind(this);

        // Custom action bar that contains the "done" button for saving changes
        ActionBar actionBar = getSupportActionBar();
        @SuppressLint("InflateParams") View customActionBarView = getLayoutInflater().inflate(R.layout.addressinfo_list_actionbar, null);
        mSwiGeofencing = (SwitchCompat) customActionBarView.findViewById(R.id.swiGeofencing);
        // Check it if enabled in prefs
        SharedPreferences preferenceManager = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean enabled = preferenceManager.getBoolean(Constants.PREF_GEOFENCING_ENABLED, Constants.PREF_GEOFENCING_ENABLED_DEFAULT);
        mSwiGeofencing.setChecked(enabled);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(customActionBarView, new ActionBar.LayoutParams(Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        // Align the up arrow with the switch - this needs to be done in a viewTreeObserver otherwise it is too early
        final View contentView = findViewById(android.R.id.content);
        contentView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public int mViewTreeObserverCount = 0;

            @Override
            public void onGlobalLayout() {
                // The first call is too early, the switch hasn't been laid out yet, so do this only the second time
                if (mViewTreeObserverCount > 0) {
                    // Show disabled indicator if needed
                    if (enabled) {
                        mConGeofencingDisabled.setVisibility(View.GONE);
                    } else {
                        alignUpArrow(); mConGeofencingDisabled.setVisibility(View.VISIBLE);
                    }
                }

                mViewTreeObserverCount++;
                // Unregister self
                if (mViewTreeObserverCount == 2) contentView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check for Google Play Services
        int res = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this); if (res != ConnectionResult.SUCCESS) {
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(res, this, REQUEST_INSTALL_PLAY_SERVICES, new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            }); dialog.show();
        }

        // Check for location settings
        if (!LocationUtil.isLocationEnabled(this)) {
            AlertDialogFragment dialog = AlertDialogFragment.newInstance(DIALOG_LOCATION_SETTINGS);
            dialog.setMessage(R.string.addressInfo_list_locationDialog_message); dialog.setPositiveButton(R.string.addressInfo_list_locationDialog_positive);
            dialog.setNegativeButton(R.string.addressInfo_list_locationDialog_negative); dialog.show(getSupportFragmentManager());
        }
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
            case R.id.action_about: onAboutClicked(); return true;

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

    private void onAboutClicked() {
        AboutActivityIntentBuilder builder = new AboutActivityIntentBuilder(); builder.setAppName(getString(R.string.app_name));
        builder.setBuildDate(BuildConfig.BUILD_DATE); builder.setGitSha1(BuildConfig.GIT_SHA1);
        builder.setAuthorCopyright(getString(R.string.about_authorCopyright)); builder.setLicense(getString(R.string.about_License));
        builder.setShareTextSubject(getString(R.string.about_shareText_subject)); builder.setShareTextBody(getString(R.string.about_shareText_body));
        builder.setBackgroundResId(R.drawable.about_bg); builder.addLink(getString(R.string.about_email_uri), getString(R.string.about_email_text));
        builder.addLink(getString(R.string.about_web_uri), getString(R.string.about_web_text));
        builder.addLink(getString(R.string.about_sources_uri), getString(R.string.about_sources_text)); builder.setIsLightIcons(true);
        startActivity(builder.build(this));
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
                    c.close();
                }

                // 2/ Find addresses for this contact
                ArrayList<StructuredPostal> structuredPostalList = new ArrayList<>(); projection = new String[] {ContactsContract.Data._ID, // 0
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
        startActivity(intent);
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
    //region

    @Override
    public void onClickPositive(int tag, Object payload) {
        switch (tag) {
            case DIALOG_LOCATION_SETTINGS: startActivity(Intent.createChooser(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), null)); break;
        }
    }

    @Override
    public void onClickNegative(int tag, Object payload) {}

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

    //endregion


    /*
     * AddressInfoCallbacks implementation.
     */
    //region

    @Override
    public void onAddressInfoClicked(AddressInfo addressInfo) {
        Log.d("addressInfo=" + addressInfo.uri);
        startAddressInfoEditActivity(addressInfo.uri);
    }

    @Override
    public void onListLoaded(boolean isEmpty) {
        // First disable any previously installed listener
        mSwiGeofencing.setOnCheckedChangeListener(null); if (isEmpty) {
            // Empty list, do not bother enabling the geofencing switch
            mSwiGeofencing.setEnabled(false); mSwiGeofencing.setChecked(false);
        } else {
            // Enable the switch
            mSwiGeofencing.setEnabled(true);
            // Check it if enabled in prefs
            SharedPreferences preferenceManager = PreferenceManager.getDefaultSharedPreferences(this);
            boolean enabled = preferenceManager.getBoolean(Constants.PREF_GEOFENCING_ENABLED, Constants.PREF_GEOFENCING_ENABLED_DEFAULT);
            mSwiGeofencing.setChecked(enabled);
            // Show disabled indicator if needed
            if (enabled) {
                mConGeofencingDisabled.setVisibility(View.GONE);
            } else {
                mConGeofencingDisabled.setVisibility(View.VISIBLE);
            }
        }
        // Install listener
        mSwiGeofencing.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onGeofencingCheckedChanged(isChecked);
            }
        });
    }

    //endregion


    private void onGeofencingCheckedChanged(boolean isChecked) {
        SharedPreferences preferenceManager = PreferenceManager.getDefaultSharedPreferences(this);
        preferenceManager.edit().putBoolean(Constants.PREF_GEOFENCING_ENABLED, isChecked).commit();
        GeofencingService.refresh(this);
        if (isChecked) {
            Toast.makeText(this, R.string.addressInfo_list_geofencingOn, Toast.LENGTH_SHORT).show();
            mConGeofencingDisabled.animate().alpha(0f).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}

                @Override
                public void onAnimationEnd(Animator animation) {
                    mConGeofencingDisabled.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationCancel(Animator animation) {}

                @Override
                public void onAnimationRepeat(Animator animation) {}
            });
        } else {
            mConGeofencingDisabled.setAlpha(0f); mConGeofencingDisabled.setVisibility(View.VISIBLE); mConGeofencingDisabled.post(new Runnable() {
                @Override
                public void run() {
                    alignUpArrow(); mConGeofencingDisabled.animate().alpha(1f).setListener(null);
                }
            });
        }
    }

    private void alignUpArrow() {
        Display display = getWindowManager().getDefaultDisplay(); Point size = new Point(); display.getSize(size); int displayWidth = size.x;

        Rect switchLocation = UiUtil.getLocationInWindow(mSwiGeofencing); Log.d("switchLocation=" + switchLocation);
        int diff = displayWidth - switchLocation.right; LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) mImgArrowUp.getLayoutParams();
        layoutParams.rightMargin = diff + mSwiGeofencing.getWidth() / 2 - mImgArrowUp.getWidth() / 2; mImgArrowUp.setLayoutParams(layoutParams);
    }
}
