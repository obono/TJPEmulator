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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class EepromActivity extends AppCompatActivity {

    private static final int HEXDUMP_COLUMNS_NORMAL = 8;
    private static final int HEXDUMP_COLUMNS_WIDE = 16;
    private static final int HEADER_LETTERS = 52;

    private static final int REQUEST_RESTORE_EEPROM = 2;
    private static final int REQUEST_BACKUP_EEPROM  = 3;

    private MyApplication   mApp;
    private TextView        mTextViewHeader;
    private TextView        mTextViewBody;
    private int             mViewWidth;
    private int             mHexDumpColumns;

    /*-----------------------------------------------------------------------*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eeprom);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mApp = (MyApplication) getApplication();
        mTextViewHeader = findViewById(R.id.textViewEepromHeader);
        mTextViewBody = findViewById(R.id.textViewEepromBody);
        ViewTreeObserver observer = mTextViewHeader.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(() -> refreshDump());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.eeprom, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.INTENT_EXTRA_EXTENSIONS, FilePickerActivity.EXTS_EEPROM);
        intent.putExtra(FilePickerActivity.INTENT_EXTRA_DIRECTORY, mApp.getPathEeprom());
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        case R.id.menuEepromClear:
            Utils.showMessageDialog(this, R.string.menuClear, R.string.messageConfirmClear,
                    (dialog, which) -> {
                        mApp.getTJPEmulator().clearEeprom();
                        refreshDump();
                    });
            return true;
        case R.id.menuEepromRestore:
            intent.putExtra(FilePickerActivity.INTENT_EXTRA_WRITEMODE, false);
            startActivityForResult(intent, REQUEST_RESTORE_EEPROM);
            return true;
        case R.id.menuEepromBackup:
            intent.putExtra(FilePickerActivity.INTENT_EXTRA_WRITEMODE, true);
            startActivityForResult(intent, REQUEST_BACKUP_EEPROM);
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_RESTORE_EEPROM:
                if (resultCode == RESULT_OK) {
                    String path = data.getStringExtra(FilePickerActivity.INTENT_EXTRA_SELECTPATH);
                    mApp.setPathEeprom(Utils.getParentPath(path));
                    if (mApp.getTJPEmulator().restoreEeprom(path)) {
                        Utils.showToast(this, R.string.messageLoadSucceeded);
                        refreshDump();
                    } else {
                        Utils.showToast(this, R.string.messageLoadFailed);
                    }
                }
                break;
            case REQUEST_BACKUP_EEPROM:
                if (resultCode == RESULT_OK) {
                    String path = data.getStringExtra(FilePickerActivity.INTENT_EXTRA_SELECTPATH);
                    mApp.setPathEeprom(Utils.getParentPath(path));
                    if (mApp.getTJPEmulator().backupEeprom(path)) {
                        Utils.showToast(this, R.string.messageSaveSucceeded);
                    } else {
                        Utils.showToast(this, R.string.messageSaveFailed);
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    /*-----------------------------------------------------------------------*/

    private void refreshDump() {
        /*  Check width  */
        int columnMax;
        if (mViewWidth == mTextViewHeader.getWidth()) {
            columnMax = mHexDumpColumns;
        } else {
            mViewWidth = mTextViewHeader.getWidth();
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(mTextViewHeader.getTextSize());
            textPaint.setTypeface(Typeface.MONOSPACE);
            if (mViewWidth < textPaint.measureText(" ") * HEADER_LETTERS) {
                columnMax = HEXDUMP_COLUMNS_NORMAL;
            } else {
                columnMax = HEXDUMP_COLUMNS_WIDE;
            }
        }

        /*  Header  */
        StringBuffer buf = new StringBuffer("    ");
        for (int column = 0; column < columnMax; column++) {
            buf.append(String.format(" +%X", column));
        }
        mTextViewHeader.setText(buf.toString());

        /*  Body  */
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int rowMax = TJPEmulator.EEPROM_SIZE / columnMax;
        byte eeprom[] = mApp.getTJPEmulator().getEeprom();
        for (int row = 0; row < rowMax; row++) {
            int start = sb.length();
            ForegroundColorSpan span = new ForegroundColorSpan(Color.GRAY);
            sb.append(String.format("%03X:", row * columnMax));
            sb.setSpan(span, start, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); 
            for (int col = 0; col < columnMax; col++) {
                sb.append(String.format(" %02X", eeprom[row * columnMax + col]));
            }
            sb.append('\n');
        }
        int length = sb.length();
        sb.delete(length - 1, length);
        mTextViewBody.setText(sb);

        /*  Adjust scroll offset  */
        if (mHexDumpColumns != columnMax) {
            ScrollView scrollView = findViewById(R.id.scrollViewEepromBody);
            int y = scrollView.getScrollY();
            scrollView.scrollTo(0, (mHexDumpColumns == HEXDUMP_COLUMNS_NORMAL) ? y / 2 : y * 2);
            mHexDumpColumns = columnMax;
        }
    }
}
