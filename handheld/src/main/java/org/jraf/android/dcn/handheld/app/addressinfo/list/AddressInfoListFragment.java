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

import java.util.List;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.jraf.android.dcn.R;
import org.jraf.android.dcn.handheld.model.addressinfo.AddressInfo;
import org.jraf.android.util.app.base.BaseFragment;

import butterknife.Bind;
import butterknife.ButterKnife;

public class AddressInfoListFragment extends BaseFragment<AddressInfoCallbacks> implements LoaderManager.LoaderCallbacks<List<AddressInfo>> {
    @Bind(R.id.rclList)
    protected RecyclerView mRclList;

    @Bind(R.id.txtEmpty)
    protected TextView mTxtEmpty;

    private AddressInfoAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View res = inflater.inflate(R.layout.addressinfo_list_list, container, false);
        ButterKnife.bind(this, res);
        mRclList.setHasFixedSize(true);
        mRclList.setLayoutManager(new LinearLayoutManager(getActivity()));

        // Don't show the empty text while loading
        mTxtEmpty.setVisibility(View.GONE);
        return res;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(0,null, this);
    }

    @Override
    public Loader<List<AddressInfo>> onCreateLoader(int id, Bundle args) {
        return new AddressInfoLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<List<AddressInfo>> loader, List<AddressInfo> data) {
        if (mAdapter == null) {
            mAdapter=new AddressInfoAdapter(getActivity(), getCallbacks());
            mRclList.setAdapter(mAdapter);
        } else {
            mAdapter.clear();
        } boolean empty = data.isEmpty(); if (empty) {
            mTxtEmpty.setVisibility(View.VISIBLE);
        } else {
            mTxtEmpty.setVisibility(View.GONE);
            mAdapter.addAll(data);
        }

        getCallbacks().onListLoaded(empty);
    }

    @Override
    public void onLoaderReset(Loader<List<AddressInfo>> loader) {
        mAdapter.clear();
    }
}
