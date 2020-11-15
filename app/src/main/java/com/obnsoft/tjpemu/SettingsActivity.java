/*
 * Copyright (C) 2020 OBONO
 * https://obono.hateblo.jp/
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

package com.obnsoft.tjpemu;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

public class SettingsActivity extends AppCompatActivity
        implements OnSharedPreferenceChangeListener {

    private static final String PREFS_KEY_REFRESH   = "refresh";
    private static final String PREFS_KEY_ABOUT     = "about";
    private static final String PREFS_KEY_LICENSE   = "license";
    private static final String PREFS_KEY_WEBSITES  = "websites";
    private static final String PREFS_DEFAULT       = "default";

    private static final Uri URI_GPL3 = Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html");

    private MyApplication   mApp;

    /*-----------------------------------------------------------------------*/

    public static class MyPreferenceFragment extends PreferenceFragmentCompat
            implements Preference.OnPreferenceClickListener {

        private SettingsActivity    mActivity;
        private MyApplication       mApp;

        public MyPreferenceFragment() {
            // Required empty public constructor
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.prefs, rootKey);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            refreshSummary();
        }

        @Override
        public boolean onPreferenceClick(Preference pref) {
            if (PREFS_KEY_ABOUT.equals(pref.getKey())) {
                Utils.showVersion(mActivity);
            } else if (PREFS_KEY_LICENSE.equals(pref.getKey())) {
                Intent intent = new Intent(Intent.ACTION_VIEW, URI_GPL3);
                mActivity.startActivity(intent);
            } else if (PREFS_KEY_WEBSITES.equals(pref.getKey())) {
                mActivity.showUrlList();
            } else {
                refreshSummary();
            }
            return true;
        }

        public void setContext(MyApplication app, SettingsActivity activity) {
            mApp = app;
            mActivity = activity;
        }

        public void refreshSummary() {
            refreshSummary(getPreferenceScreen());
        }

        public void refreshSummary(PreferenceGroup prefGroup) {
            for (int i = 0; i < prefGroup.getPreferenceCount(); i++) {
                setSummary(prefGroup.getPreference(i));
            }
        }

        public void setSummary(Preference pref) {
            if (pref != null) {
                setSummary(pref, pref.getKey());
            }
        }

        public void setSummary(String key) {
            if (key != null) {
                setSummary(findPreference(key), key);
            }
        }

        public void setSummary(Preference pref, String key) {
            SharedPreferences sharedPrefs = mApp.getSharedPreferences();
            if (pref instanceof ListPreference) {
                ListPreference listPref = (ListPreference) pref;
                listPref.setValue(sharedPrefs.getString(key, PREFS_DEFAULT));
                listPref.setSummary(listPref.getEntry());
            } else if (pref instanceof CheckBoxPreference) {
                CheckBoxPreference checkBoxPref = (CheckBoxPreference) pref;
                checkBoxPref.setChecked(sharedPrefs.getBoolean(key, false));
            } else if (pref instanceof EditTextPreference) {
                EditTextPreference editTextPref = (EditTextPreference) pref;
                editTextPref.setText(sharedPrefs.getString(key, PREFS_DEFAULT));
                editTextPref.setSummary(editTextPref.getText());
            } else if (pref instanceof PreferenceGroup) {
                refreshSummary((PreferenceGroup) pref);
            } else {
                pref.setOnPreferenceClickListener(this);
            }
        }
    }

/*-----------------------------------------------------------------------*/

    private MyPreferenceFragment mFragment = new MyPreferenceFragment();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mApp = (MyApplication) getApplication();
        mFragment.setContext(mApp, this);
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settingsContainer, mFragment)
            .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = mFragment.getPreferenceScreen().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences prefs = mFragment.getPreferenceScreen().getSharedPreferences();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        mFragment.setSummary(key);
        if (PREFS_KEY_REFRESH.equals(key)) {
            Native.setRefreshTiming(prefs.getBoolean(PREFS_KEY_REFRESH, false));
        }
    }

    /*-----------------------------------------------------------------------*/

    protected void showUrlList() {
        final String[] items = getResources().getStringArray(R.array.bookmarkArray);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    Uri uri = Uri.parse(items[which]);
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Utils.showListDialog(this, R.string.prefsWebsites, items, listener);
    }

}
