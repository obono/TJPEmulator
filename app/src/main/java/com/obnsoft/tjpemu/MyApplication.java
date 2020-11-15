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

import java.io.File;
import java.util.Arrays;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

public class MyApplication extends Application {

    private static final String PREFS_KEY_TOOLBAR       = "toolbar";
    private static final String PREFS_KEY_FPS           = "fps";
    private static final String PREFS_KEY_REFRESH       = "refresh";
    private static final String PREFS_KEY_CONFIRMQUIT   = "confirm_quit";
    private static final String PREFS_KEY_PATH_FLASH    = "path_flash";
    private static final String PREFS_KEY_PATH_EEPROM   = "path_eeprom";

    private static final boolean PREFS_DEFAULT_TOOLBAR  = false;
    private static final String PREFS_DEFAULT_FPS       = "30";
    private static final boolean PREFS_DEFAULT_REFRESH  = false;
    private static final boolean PREFS_DEFAULT_CONFIRMQUIT = true;

    private TJPEmulator     mTJPEmulator;

    /*-----------------------------------------------------------------------*/

    @Override
    public void onCreate() {
        super.onCreate();
        mTJPEmulator = new TJPEmulator(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    public TJPEmulator getTJPEmulator() {
        return mTJPEmulator;
    };

    /*-----------------------------------------------------------------------*/

    public SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    public boolean getShowToolbar() {
        return getSharedPreferences().getBoolean(PREFS_KEY_TOOLBAR, PREFS_DEFAULT_TOOLBAR);
    }

    public String getEmulationFpsString() {
        return getSharedPreferences().getString(PREFS_KEY_FPS, PREFS_DEFAULT_FPS);
    }

    public float getEmulationFps() {
        return Float.parseFloat(getEmulationFpsString());
    }

    public int getEmulationFpsItemPos() {
        String[] ary = getResources().getStringArray(R.array.entryValuesFps);
        return Arrays.asList(ary).indexOf(getEmulationFpsString());
    }

    public boolean setEmulationFpsByItemPos(int itemPos) {
        String[] ary = getResources().getStringArray(R.array.entryValuesFps);
        return putStringToSharedPreferences(PREFS_KEY_FPS, ary[itemPos]);
    }

    public boolean getEmulationPostRefresh() {
        return getSharedPreferences().getBoolean(PREFS_KEY_REFRESH, PREFS_DEFAULT_REFRESH);
    }

    public boolean getConfirmQuit() {
        return getSharedPreferences().getBoolean(PREFS_KEY_CONFIRMQUIT, PREFS_DEFAULT_CONFIRMQUIT);
    }

    public String getPathFlash() {
        SharedPreferences sharedPrefs = getSharedPreferences();
        String path = sharedPrefs.getString(PREFS_KEY_PATH_FLASH, null);
        if (path == null || !(new File(path).exists())) {
            path = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        return path;
    }

    public boolean setPathFlash(String path) {
        return putStringToSharedPreferences(PREFS_KEY_PATH_FLASH, path);
    }

    public String getPathEeprom() {
        SharedPreferences sharedPrefs = getSharedPreferences();
        return sharedPrefs.getString(PREFS_KEY_PATH_EEPROM, getPathFlash());
    }

    public boolean setPathEeprom(String path) {
        return putStringToSharedPreferences(PREFS_KEY_PATH_EEPROM, path);
    }

    public boolean putStringToSharedPreferences(String key, String value) {
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putString(key, value);
        return editor.commit();
    }

}
