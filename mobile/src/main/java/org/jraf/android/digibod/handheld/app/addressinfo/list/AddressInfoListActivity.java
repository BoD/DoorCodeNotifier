package org.jraf.android.digibod.handheld.app.addressinfo.list;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.jraf.android.digibod.R;
import org.jraf.android.digibod.handheld.app.addressinfo.edit.AddressInfoEditActivity;
import org.jraf.android.digibod.handheld.model.addressinfo.AddressInfo;
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
        ButterKnife.inject(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
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
                Cursor c = getActivity().getContentResolver().query(contactLookupUri, projection, null, null, null);
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
                c = getActivity().getContentResolver().query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArg, null);
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
                    getActivity().createAddressAndEdit(contactId);
                } else if (structuredPostalList.size() == 1) {
                    // The selected contact has 1 address
                    StructuredPostal structuredPostal = structuredPostalList.get(0);
                    if (AddressInfo.isAugmented(structuredPostal.formattedAddress)) {
                        // The address is already augmented: create a new address now and edit it
                        getActivity().createAddressAndEdit(contactId);
                    } else {
                        // The address is not augmented: edit it now
                        getActivity().startAddressInfoEditActivity(structuredPostal.uri);
                    }
                } else {
                    // The selected contact has several addresses: ask the user which one he wants to edit
                    getActivity().chooseAddressToEdit(contactId, structuredPostalList);
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
        // TODO
    }

    private void chooseAddressToEdit(long contactId, ArrayList<StructuredPostal> structuredPostalList) {
        Log.d();
        AlertDialogFragment dialogFragment = AlertDialogFragment.newInstance(DIALOG_CHOOSE_ADDRESS_TO_EDIT);
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
}
