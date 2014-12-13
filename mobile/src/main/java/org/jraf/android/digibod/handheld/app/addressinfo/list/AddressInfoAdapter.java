package org.jraf.android.digibod.handheld.app.addressinfo.list;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.jraf.android.digibod.R;
import org.jraf.android.digibod.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.digibod.handheld.util.picasso.RoundTransformation;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class AddressInfoAdapter extends RecyclerView.Adapter<AddressInfoAdapter.ViewHolder> {
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private List<AddressInfo> mObjects = new ArrayList<>();


    public static class ViewHolder extends RecyclerView.ViewHolder {
        @InjectView(R.id.txtContactDisplayName)
        public TextView txtContactDisplayName;

        @InjectView(R.id.txtFormattedAddress)
        public TextView txtFormattedAddress;

        @InjectView(R.id.imgPhoto)
        public ImageView imgPhoto;

        @InjectView(R.id.conFields)
        public ViewGroup conFields;

        public ViewHolder(View v) {
            super(v);
            ButterKnife.inject(this, v);
        }
    }

    public AddressInfoAdapter(Context context) {
        mContext = context;
        mLayoutInflater = LayoutInflater.from(mContext);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = mLayoutInflater.inflate(R.layout.addressinfo_list_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AddressInfo addressInfo = mObjects.get(position);
        // Contact display name
        holder.txtContactDisplayName.setText(addressInfo.contactInfo.displayName);

        // Contact photo
        Picasso.with(mContext).load(addressInfo.contactInfo.uri).transform(RoundTransformation.get()).into(holder.imgPhoto);

        // Formatted address
        holder.txtFormattedAddress.setText(addressInfo.formattedAddress);

        holder.conFields.removeAllViews();

        // Codes
        int i = 1;
        for (String code : addressInfo.codeList) {
            View codeView = mLayoutInflater.inflate(R.layout.addressinfo_list_item_code, (ViewGroup) holder.conFields, false);

            TextView txtTitle = (TextView) codeView.findViewById(R.id.txtTitle);
            if (addressInfo.codeList.size() == 1) {
                txtTitle.setVisibility(View.GONE);
            } else {
                txtTitle.setText(mContext.getString(R.string.addressinfo_list_code, i));
            }

            TextView txtValue = (TextView) codeView.findViewById(R.id.txtValue);
            txtValue.setText(code);

            holder.conFields.addView(codeView);
            i++;
        }

        // Other info
        if (addressInfo.otherInfo != null) {
            View otherInfoView = mLayoutInflater.inflate(R.layout.addressinfo_list_item_otherinfo, (ViewGroup) holder.conFields, false);
            TextView txtValue = (TextView) otherInfoView.findViewById(R.id.txtValue);
            txtValue.setText(addressInfo.otherInfo);
            holder.conFields.addView(otherInfoView);
        }

    }

    @Override
    public int getItemCount() {
        return mObjects.size();
    }

    public void clear() {
        mObjects.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<AddressInfo> data) {
        mObjects.addAll(data);
        notifyDataSetChanged();
    }


}
