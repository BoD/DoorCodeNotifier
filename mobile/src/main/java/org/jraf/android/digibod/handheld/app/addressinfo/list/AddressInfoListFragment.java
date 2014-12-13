package org.jraf.android.digibod.handheld.app.addressinfo.list;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.jraf.android.digibod.R;
import org.jraf.android.digibod.handheld.model.addressinfo.AddressInfo;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class AddressInfoListFragment extends Fragment implements LoaderManager.LoaderCallbacks<List<AddressInfo>> {
    @InjectView(R.id.rclList)
    protected RecyclerView mRclList;

    private AddressInfoAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View res = inflater.inflate(R.layout.addressinfo_list_list, container, false);
        ButterKnife.inject(this, res);
        mRclList.setHasFixedSize(true);
        mRclList.setLayoutManager(new LinearLayoutManager(getActivity()));
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
            mAdapter=new AddressInfoAdapter(getActivity());
            mRclList.setAdapter(mAdapter);
        } else {
            mAdapter.clear();
        }
        mAdapter.addAll(data);
    }

    @Override
    public void onLoaderReset(Loader<List<AddressInfo>> loader) {
        mAdapter.clear();
    }
}
