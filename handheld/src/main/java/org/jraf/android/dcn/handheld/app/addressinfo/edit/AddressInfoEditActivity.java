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
package org.jraf.android.dcn.handheld.app.addressinfo.edit;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import org.jraf.android.dcn.R;
import org.jraf.android.dcn.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.dcn.handheld.util.picasso.RoundTransformation;
import org.jraf.android.util.async.Task;
import org.jraf.android.util.async.TaskFragment;
import org.jraf.android.util.dialog.AlertDialogFragment;
import org.jraf.android.util.dialog.AlertDialogListener;
import org.jraf.android.util.log.wrapper.Log;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class AddressInfoEditActivity extends ActionBarActivity implements AlertDialogListener {
    private static final int DIALOG_DELETE = 0;

    @InjectView(R.id.txtContactDisplayName)
    protected TextView mTxtContactDisplayName;

    @InjectView(R.id.edtFormattedAddress)
    protected EditText mEdtFormattedAddress;

    @InjectView(R.id.imgPhoto)
    protected ImageView mImgPhoto;

    @InjectView(R.id.conFields)
    protected ViewGroup mConFields;

    private EditText mEdtOtherInfo;

    private Uri mAddressUri;
    private AddressInfo mAddressInfo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.addressinfo_edit);

        // Custom action bar that contains the "done" button for saving changes
        ActionBar actionBar = getSupportActionBar();
        @SuppressLint("InflateParams") View customActionBarView = getLayoutInflater().inflate(R.layout.addressinfo_edit_actionbar, null);
        View saveMenuItem = customActionBarView.findViewById(R.id.save_menu_item);
        saveMenuItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDoneClicked();
            }
        });
        TextView txtTitle = (TextView) customActionBarView.findViewById(R.id.title);
        txtTitle.setText(getResources().getString(R.string.addressInfo_edit_title));
        // Show the custom action bar but hide the home icon and title
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setCustomView(customActionBarView);

        ButterKnife.inject(this);

        mAddressUri = getIntent().getData();
        loadData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.addressinfo_edit, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                onDeleteClicked();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void loadData() {
        new TaskFragment(new Task<AddressInfoEditActivity>() {
            @Override
            protected void doInBackground() throws Throwable {
                AddressInfoEditActivity a = getActivity();
                Uri addressUri = a.mAddressUri;

                String[] projection = {ContactsContract.Data.CONTACT_ID, // 0
                        ContactsContract.Contacts.DISPLAY_NAME, // 1
                        ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, // 2
                        ContactsContract.Contacts.LOOKUP_KEY, // 3
                };
                Cursor c = a.getContentResolver().query(addressUri, projection, null, null, null);
                try {
                    if (c.moveToNext()) {
                        String formattedAddress = c.getString(2);
                        AddressInfo addressInfo;
                        if (AddressInfo.isAugmented(formattedAddress)) {
                            // Augmented (existing AddressInfo)
                            addressInfo = AddressInfo.parseAugmented(formattedAddress);
                        } else {
                            // New AddressInfo
                            addressInfo = new AddressInfo();
                            addressInfo.formattedAddress = formattedAddress;
                        }
                        addressInfo.uri = addressUri;
                        addressInfo.contactInfo.uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.getLong(0));
                        addressInfo.contactInfo.contentLookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, c.getString(3));
                        addressInfo.contactInfo.displayName = c.getString(1);

                        a.mAddressInfo = addressInfo;
                    } else {
                        // Should not normally happen
                        throw new Exception("Could not find uri " + addressUri);
                    }
                } finally {
                    c.close();
                }
            }

            @Override
            protected void onPostExecuteFail() {
                super.onPostExecuteFail();
                getActivity().finish();
            }

            @Override
            protected void onPostExecuteOk() {
                getActivity().onDataLoaded();
            }
        }.toastFail(R.string.addressInfo_edit_loadError)).execute(getSupportFragmentManager());
    }

    private void onDataLoaded() {
        // Contact display name
        mTxtContactDisplayName.setText(mAddressInfo.contactInfo.displayName);

        // Contact photo
        Picasso.with(this).load(mAddressInfo.contactInfo.uri).transform(RoundTransformation.get()).into(mImgPhoto);

        // Formatted address
        if (mAddressInfo.formattedAddress != null) mEdtFormattedAddress.append(mAddressInfo.formattedAddress);

        mConFields.removeAllViews();

        // Codes
        int i = 1;
        for (String code : mAddressInfo.codeList) {
            final View codeView = getLayoutInflater().inflate(R.layout.addressinfo_edit_code, mConFields, false);

//            TextView txtTitle = (TextView) codeView.findViewById(R.id.txtTitle);
//            if (mAddressInfo.codeList.size() == 1) {
//                txtTitle.setVisibility(View.GONE);
//            } else {
//                txtTitle.setText(getString(R.string.addressinfo_list_code, i));
//            }

            EditText edtValue = (EditText) codeView.findViewById(R.id.edtValue);
            edtValue.append(code);

            codeView.findViewById(R.id.btnRemove).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mConFields.removeView(codeView);
                }
            });

            mConFields.addView(codeView);

            // Focus the first code
            if (i == 1) edtValue.requestFocus();

            i++;
        }

        // If there are no codes, show an empty code EditView
        if (i == 1) {
            final View codeView = getLayoutInflater().inflate(R.layout.addressinfo_edit_code, mConFields, false);

            EditText edtValue = (EditText) codeView.findViewById(R.id.edtValue);
            codeView.findViewById(R.id.btnRemove).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mConFields.removeView(codeView);
                }
            });
            mConFields.addView(codeView);

            // Focus it
            edtValue.requestFocus();
        }

        // Add code button
        final View addCodeView = getLayoutInflater().inflate(R.layout.addressinfo_edit_addcode, mConFields, false);
        addCodeView.findViewById(R.id.btnAddCode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addCodeView();
            }
        });
        mConFields.addView(addCodeView);

        // Other info
        View otherInfoView = getLayoutInflater().inflate(R.layout.addressinfo_edit_otherinfo, mConFields, false);
        mEdtOtherInfo = (EditText) otherInfoView.findViewById(R.id.edtOtherInfo);

        otherInfoView.findViewById(R.id.btnOtherInfoClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEdtOtherInfo.setText(null);
                mEdtOtherInfo.requestFocus();
            }
        });
        if (mAddressInfo.otherInfo != null) mEdtOtherInfo.append(mAddressInfo.otherInfo);
        mConFields.addView(otherInfoView);

        // Enable layout transitions
        LayoutTransition layoutTransition = new LayoutTransition();
        mConFields.setLayoutTransition(layoutTransition);
    }

    private void addCodeView() {
        final View codeView = getLayoutInflater().inflate(R.layout.addressinfo_edit_code, mConFields, false);
        EditText edtValue = (EditText) codeView.findViewById(R.id.edtValue);
        codeView.findViewById(R.id.btnRemove).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mConFields.removeView(codeView);
            }
        });

        // Insert it before the "Add" button which is the second to last child
        int index = mConFields.getChildCount() - 2;
        mConFields.addView(codeView, index);

        // Focus it now
        edtValue.requestFocus();
    }

    private void onDoneClicked() {
        // Code list
        int childCount = mConFields.getChildCount();
        List<String> codeList = new ArrayList<>(childCount);
        for (int i = 0; i < childCount - 2; i++) {
            View child = mConFields.getChildAt(i);
            EditText edtValue = (EditText) child.findViewById(R.id.edtValue);
            String code = edtValue.getText().toString().trim();
            if (!code.isEmpty()) codeList.add(code);
        }
        mAddressInfo.codeList = codeList;

        // Other info
        mAddressInfo.otherInfo = mEdtOtherInfo.getText().toString().trim();

        // Formatted address
        final String formattedAddress = mEdtFormattedAddress.getText().toString().trim();
        mAddressInfo.formattedAddress = formattedAddress;
        // Disallow empty address
        if (TextUtils.isEmpty(formattedAddress)) {
            mEdtFormattedAddress.setError(getString(R.string.addressInfo_edit_emptyAddress));
            return;
        }

        new TaskFragment(new Task<AddressInfoEditActivity>() {
            @Override
            protected void doInBackground() throws Throwable {
                AddressInfoEditActivity a = getActivity();

                // Geocoding
                Log.d("Geocoding...");
                Geocoder geocoder = new Geocoder(a);
                List<Address> addressList = geocoder.getFromLocationName(formattedAddress, 1);
                Log.d("addressList=" + addressList);
                if (addressList == null || addressList.isEmpty()) {
                    Log.w("Could not geocode address '" + formattedAddress + "'");
                    throw new Exception("Could not geocode");
                }
                Address address = addressList.get(0);
                a.mAddressInfo.latitude = address.getLatitude();
                a.mAddressInfo.longitude = address.getLongitude();
                a.mAddressInfo.persist(a);
            }

            @Override
            protected void onPostExecuteOk() {
                getActivity().finish();
            }

            @Override
            protected void onPostExecuteFail() {
                AddressInfoEditActivity a = getActivity();
                Toast.makeText(a, R.string.addressInfo_edit_couldNotGeocode, Toast.LENGTH_LONG).show();
                a.mEdtFormattedAddress.setError(a.getString(R.string.addressInfo_edit_couldNotGeocode));
            }
        }).execute(getSupportFragmentManager());
    }

    private void onDeleteClicked() {
        AlertDialogFragment alertDialogFragment = AlertDialogFragment.newInstance(DIALOG_DELETE);
        alertDialogFragment.setMessage(R.string.common_confirm_delete);
        alertDialogFragment.setNegativeButton(android.R.string.cancel);
        alertDialogFragment.setPositiveButton(android.R.string.ok);
        alertDialogFragment.show(getSupportFragmentManager());
    }

    private void delete() {
        new TaskFragment(new Task<AddressInfoEditActivity>() {
            @Override
            protected void doInBackground() throws Throwable {
                AddressInfoEditActivity a = getActivity();
                a.mAddressInfo.delete(a);
            }

            @Override
            protected void onPostExecuteOk() {
                getActivity().finish();
            }
        }).execute(getSupportFragmentManager());
    }


    /*
     * AlertDialogListener implementation.
     */

    @Override
    public void onClickPositive(int tag, Object payload) {
        delete();
    }

    @Override
    public void onClickNegative(int tag, Object payload) {
    }

    @Override
    public void onClickListItem(int tag, int index, Object payload) {
    }
}
