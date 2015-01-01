package org.jraf.android.digibod.handheld.app.addressinfo.edit;

import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.jraf.android.digibod.R;
import org.jraf.android.digibod.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.digibod.handheld.util.picasso.RoundTransformation;
import org.jraf.android.util.async.Task;
import org.jraf.android.util.async.TaskFragment;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class AddressInfoEditActivity extends ActionBarActivity {
    @InjectView(R.id.txtContactDisplayName)
    protected TextView mTxtContactDisplayName;

    @InjectView(R.id.txtFormattedAddress)
    protected TextView mTxtFormattedAddress;

    @InjectView(R.id.imgPhoto)
    protected ImageView mImgPhoto;

    @InjectView(R.id.conFields)
    protected ViewGroup mConFields;

    private Uri mAddressUri;
    private AddressInfo mAddressInfo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.addressinfo_list_item);
        ButterKnife.inject(this);

        mAddressUri = getIntent().getData();
        loadData();
    }

    private void loadData() {
        new TaskFragment(new Task<AddressInfoEditActivity>() {
            @Override
            protected void doInBackground() throws Throwable {
                Uri addressUri = getActivity().mAddressUri;

                String[] projection = {
                        ContactsContract.Data.CONTACT_ID, // 0
                        ContactsContract.Contacts.DISPLAY_NAME, // 1
                        ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, // 2
                };
                Cursor c = getActivity().getContentResolver().query(addressUri, projection, null, null, null);
                if (c.moveToNext()) {
                    AddressInfo addressInfo = AddressInfo.parse(c.getString(2));
                    addressInfo.uri = addressUri;
                    addressInfo.contactInfo.uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.getLong(0));
                    addressInfo.contactInfo.displayName = c.getString(1);

                    getActivity().mAddressInfo = addressInfo;
                } else {
                    throw new Exception("Could not find uri " + addressUri);
                }
            }

            @Override
            protected void onPostExecuteFail() {
                super.onPostExecuteFail();
                finish();
            }

            @Override
            protected void onPostExecuteOk() {
                onDataLoaded();
            }
        }.toastFail(R.string.addressinfo_edit_loadError)).execute(getSupportFragmentManager());
    }

    private void onDataLoaded() {
        // Contact display name
        mTxtContactDisplayName.setText(mAddressInfo.contactInfo.displayName);

        // Contact photo
        Picasso.with(this).load(mAddressInfo.contactInfo.uri).transform(RoundTransformation.get()).into(mImgPhoto);

        // Formatted address
        mTxtFormattedAddress.setText(mAddressInfo.formattedAddress);

        mConFields.removeAllViews();

        // Codes
        int i = 1;
        for (String code : mAddressInfo.codeList) {
            View codeView = getLayoutInflater().inflate(R.layout.addressinfo_list_item_code, (ViewGroup) mConFields, false);

            TextView txtTitle = (TextView) codeView.findViewById(R.id.txtTitle);
            if (mAddressInfo.codeList.size() == 1) {
                txtTitle.setVisibility(View.GONE);
            } else {
                txtTitle.setText(getString(R.string.addressinfo_list_code, i));
            }

            TextView txtValue = (TextView) codeView.findViewById(R.id.txtValue);
            txtValue.setText(code);

            mConFields.addView(codeView);
            i++;
        }

        // Other info
        if (mAddressInfo.otherInfo != null) {
            View otherInfoView = getLayoutInflater().inflate(R.layout.addressinfo_list_item_otherinfo, (ViewGroup) mConFields, false);
            TextView txtValue = (TextView) otherInfoView.findViewById(R.id.txtValue);
            txtValue.setText(mAddressInfo.otherInfo);
            mConFields.addView(otherInfoView);
        }
    }
}
