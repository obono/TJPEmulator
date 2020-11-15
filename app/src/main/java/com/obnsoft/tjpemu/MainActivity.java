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
import java.util.Locale;

import com.obnsoft.tjpemu.MyAsyncTaskWithDialog.Result;
import com.obnsoft.tjpemu.Utils.ResultHandler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OPEN_FLASH = 1;

    private MyApplication       mApp;
    private TJPEmulator         mTJPEmulator;
    private EmulatorScreenView  mEmulatorScreenView;
    private RelativeLayout      mLayoutToolbar;
    private Spinner             mSpinnerToolFps;
    private ImageButton         mButtonToolCaptureMovie;
    private String              mCurrentPath;

    /*-----------------------------------------------------------------------*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mApp = (MyApplication) getApplication();
        mTJPEmulator = mApp.getTJPEmulator();

        mEmulatorScreenView = findViewById(R.id.emulatorScreenView);
        mLayoutToolbar = findViewById(R.id.relativeLayoutToolBar);
        mSpinnerToolFps = findViewById(R.id.spinnerToolFps);
        mButtonToolCaptureMovie = findViewById(R.id.buttonToolCaptureMovie);

        mSpinnerToolFps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mApp.setEmulationFpsByItemPos(position);
                mTJPEmulator.setFps(mApp.getEmulationFps());
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });

        Intent intent = getIntent();
        if (intent != null) {
            handleIntent(intent);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menuMainOpen) {
            Intent intent = new Intent(this, FilePickerActivity.class);
            intent.putExtra(FilePickerActivity.INTENT_EXTRA_EXTENSIONS,
                    FilePickerActivity.EXTS_FLASH);
            intent.putExtra(FilePickerActivity.INTENT_EXTRA_WRITEMODE, false);
            intent.putExtra(FilePickerActivity.INTENT_EXTRA_DIRECTORY, mApp.getPathFlash());
            startActivityForResult(intent, REQUEST_OPEN_FLASH);
            return true;
        } else if (id == R.id.menuMainEeprom) {
            startActivity(new Intent(this, EepromActivity.class));
            return true;
        } else if (id == R.id.menuMainSettings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_OPEN_FLASH:
                if (resultCode == RESULT_OK) {
                    String path = data.getStringExtra(FilePickerActivity.INTENT_EXTRA_SELECTPATH);
                    mApp.setPathFlash(Utils.getParentPath(path));
                    startEmulation(path);
                }
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (mApp.getConfirmQuit()) {
            Utils.showMessageDialog(this, R.string.menuQuit, R.string.messageConfirmQuit,
                    (dialog, which) -> MainActivity.super.onBackPressed());
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        if (mTJPEmulator.isCapturing()) {
            mTJPEmulator.stopCapturing();
        }
        mTJPEmulator.stopEmulation();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLayoutToolbar.setVisibility((mApp.getShowToolbar()) ? View.VISIBLE : View.INVISIBLE);
        mSpinnerToolFps.setSelection(mApp.getEmulationFpsItemPos(), false);
        refreshCaptureVideoButtonColor();
        mTJPEmulator.bindEmulatorView(mEmulatorScreenView);
        if (!mTJPEmulator.isEmulating()) {
            mTJPEmulator.startEmulation();
        }
    }

    @Override
    protected void onDestroy() {
        mTJPEmulator.bindEmulatorView(null);
        mTJPEmulator.finishEmulation();
        mEmulatorScreenView.onDestroy();
        Utils.cleanCacheFiles(this);
        super.onDestroy();
    }

    public void onClickReset(View v) {
        if (mTJPEmulator.isEmulating()) {
            startEmulation(mCurrentPath);
        }
    }

    public void onClickCaptureShot(View v) {
        if (mTJPEmulator.isEmulating()) {
            mTJPEmulator.requestOneShot();
        }
    }

    public void onClickCaptureMovie(View v) {
        if (mTJPEmulator.isEmulating()) {
            if (!mTJPEmulator.isCapturing()) {
                mTJPEmulator.startCapturing();
            } else {
                mTJPEmulator.stopCapturing();
            }
            refreshCaptureVideoButtonColor();
        }
    }

    /*-----------------------------------------------------------------------*/

    private void startEmulation(String path) {
        if (path != null && mTJPEmulator.initializeEmulation(path)) {
            mCurrentPath = path;
            mTJPEmulator.startEmulation();
        } else {
            Utils.showToast(this, R.string.messageEmulateFailed);
        }
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        Uri uri = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && uri != null) {
            Utils.downloadFile(this, uri, new ResultHandler() {
                @Override
                public void handleResult(Result result, File file) {
                    switch (result) {
                    case FAILED:
                        Utils.showToast(MainActivity.this, R.string.messageDownloadFailed);
                        // go to following code
                    default:
                    case CANCELLED:
                        file.delete();
                        break;
                    case SUCCEEDED:
                        final String path = file.getAbsolutePath();
                        if (path.toLowerCase(Locale.getDefault())
                                .endsWith(FilePickerActivity.EXT_EEPROM)) {
                            Utils.showMessageDialog(MainActivity.this, R.string.menuEeprom,
                                    R.string.messageConfirmLoad,
                                    (dialog, which) -> mTJPEmulator.restoreEeprom(path));
                        } else {
                            Utils.showMessageDialog(MainActivity.this, R.string.menuOpen,
                                    R.string.messageConfirmLoad,
                                    (dialog, which) -> startEmulation(path));
                        }
                        break;
                    }
                }
            });
        }
    }

    private void refreshCaptureVideoButtonColor() {
        if (mTJPEmulator.isEmulating()) {
            if (mTJPEmulator.isCapturing()) {
                mButtonToolCaptureMovie.setColorFilter(Color.RED);
            } else {
                mButtonToolCaptureMovie.setColorFilter(null);
            }
        }
    }

}
