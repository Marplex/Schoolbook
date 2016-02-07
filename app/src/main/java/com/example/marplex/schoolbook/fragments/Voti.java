package com.example.marplex.schoolbook.fragments;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.marplex.schoolbook.DashboardActivity;
import com.example.marplex.schoolbook.R;
import com.example.marplex.schoolbook.fragments.tabs.FirstPeriod;
import com.example.marplex.schoolbook.fragments.tabs.Reminds;


/**
 * A simple {@link Fragment} subclass.
 */
public class Voti extends Fragment {

    private ViewPager pager;
    public Voti() {
        // Required empty public constructor
    }

    @Override
    public void onPause() {
        super.onPause();
        onDestroy();
        onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_voti, container, false);

        pager = (ViewPager)rootView.findViewById(R.id.viewpager);
        pager.setAdapter(new SectionsPagerAdapter(this.getChildFragmentManager()));

        ( (DashboardActivity) getActivity() ).tabLayout.setupWithViewPager(pager);

        return rootView;
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public android.support.v4.app.Fragment getItem(int position) {
            switch (position){
                case 0: return FirstPeriod.newInstance();
                case 1: return Reminds.newInstance();
                default: return FirstPeriod.newInstance();
            }
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "1° Periodo";
                case 1:
                    return "2° Periodo";
                default: return null;
            }
        }

    }

}