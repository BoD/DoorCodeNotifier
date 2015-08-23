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
import java.util.List;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import org.jraf.android.dcn.R;
import org.jraf.android.dcn.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.dcn.handheld.util.picasso.RoundTransformation;

import com.squareup.picasso.Picasso;

import butterknife.Bind;
import butterknife.ButterKnife;


public class AddressInfoAdapter extends RecyclerView.Adapter<AddressInfoAdapter.ViewHolder> {
    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final AddressInfoCallbacks mCallbacks;
    private List<AddressInfo> mObjects = new ArrayList<>();


    public static class ViewHolder extends RecyclerView.ViewHolder {
        @Bind(R.id.txtContactDisplayName)
        public TextView txtContactDisplayName;

        @Bind(R.id.txtFormattedAddress)
        public TextView txtFormattedAddress;

        @Bind(R.id.imgPhoto)
        public ImageView imgPhoto;

        @Bind(R.id.conFields)
        public ViewGroup conFields;

        public ViewHolder(View v, Context context) {
            super(v);
            ButterKnife.bind(this, v);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // HACK!  Adding fades to state list, so it looks nicer than the theme default - only for pre lollipop
                StateListDrawable foreground = (StateListDrawable) ((CardView) itemView).getForeground();
                int animDuration = context.getResources().getInteger(android.R.integer.config_shortAnimTime);
                foreground.setEnterFadeDuration(animDuration / 2);
                foreground.setExitFadeDuration(animDuration);
            }
        }
    }

    public AddressInfoAdapter(Context context, AddressInfoCallbacks callbacks) {
        mContext = context;
        mLayoutInflater = LayoutInflater.from(mContext);
        mCallbacks = callbacks;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = mLayoutInflater.inflate(R.layout.addressinfo_list_item, parent, false);
        return new ViewHolder(v, mContext);
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
            View codeView = mLayoutInflater.inflate(R.layout.addressinfo_list_item_code, holder.conFields, false);

            TextView txtTitle = (TextView) codeView.findViewById(R.id.txtTitle);
            if (addressInfo.codeList.size() == 1) {
                txtTitle.setVisibility(View.GONE);
            } else {
                txtTitle.setText(mContext.getString(R.string.addressInfo_list_code, i));
            }

            TextView txtValue = (TextView) codeView.findViewById(R.id.txtValue);
            txtValue.setText(code);

            holder.conFields.addView(codeView);
            i++;
        }

        // Other info
        if (addressInfo.otherInfo != null) {
            View otherInfoView = mLayoutInflater.inflate(R.layout.addressinfo_list_item_otherinfo, holder.conFields, false);
            TextView txtValue = (TextView) otherInfoView.findViewById(R.id.txtValue);
            txtValue.setText(addressInfo.otherInfo);
            holder.conFields.addView(otherInfoView);
        }

        // Adjust margins for first and last items
        MarginLayoutParams layoutParams = (MarginLayoutParams) holder.itemView.getLayoutParams();
        int topMargin;
        int bottomMargin;
        if (position == 0) {
            topMargin = R.dimen.addressinfo_list_item_margin_vertical_adjustment;
            bottomMargin = R.dimen.addressinfo_list_item_margin_vertical;
        } else if (position == getItemCount() - 1) {
            topMargin = R.dimen.addressinfo_list_item_margin_vertical;
            bottomMargin = R.dimen.addressinfo_list_item_margin_vertical_adjustment;
        } else {
            topMargin = R.dimen.addressinfo_list_item_margin_vertical;
            bottomMargin = R.dimen.addressinfo_list_item_margin_vertical;
        }
        layoutParams.topMargin = mContext.getResources().getDimensionPixelOffset(topMargin);
        layoutParams.bottomMargin = mContext.getResources().getDimensionPixelOffset(bottomMargin);
        holder.itemView.setLayoutParams(layoutParams);

        // Callback
        holder.itemView.setTag(position);
        holder.itemView.setOnClickListener(mOnClickListener);
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int position = (int) v.getTag();
            AddressInfo addressInfo = mObjects.get(position);
            mCallbacks.onAddressInfoClicked(addressInfo);
        }
    };

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
