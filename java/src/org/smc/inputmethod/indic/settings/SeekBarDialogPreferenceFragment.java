/*
 * Copyright (C) 2026 Jishnu Mohan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.smc.inputmethod.indic.settings;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.android.inputmethod.latin.R;

import org.smc.inputmethod.indic.settings.SeekBarDialogPreference.ValueProxy;

/**
 * Dialog hosting the seek bar for {@link SeekBarDialogPreference}. The data and value logic live
 * on the preference; this fragment only drives the dialog UI (androidx splits the two).
 */
public final class SeekBarDialogPreferenceFragment extends PreferenceDialogFragmentCompat
        implements SeekBar.OnSeekBarChangeListener {

    private SeekBar mSeekBar;
    private TextView mValueView;

    static SeekBarDialogPreferenceFragment newInstance(final String key) {
        final SeekBarDialogPreferenceFragment fragment = new SeekBarDialogPreferenceFragment();
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    private SeekBarDialogPreference pref() {
        return (SeekBarDialogPreference) getPreference();
    }

    private int getProgressFromValue(final int value) {
        return value - pref().getMinValue();
    }

    private int getValueFromProgress(final int progress) {
        return progress + pref().getMinValue();
    }

    private int clipValue(final int value) {
        final SeekBarDialogPreference pref = pref();
        final int clippedValue = Math.min(pref.getMaxValue(),
                Math.max(pref.getMinValue(), value));
        if (pref.getStepValue() <= 1) {
            return clippedValue;
        }
        return clippedValue - (clippedValue % pref.getStepValue());
    }

    private int getClippedValueFromProgress(final int progress) {
        return clipValue(getValueFromProgress(progress));
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);
        final ValueProxy proxy = pref().getValueProxy();
        mSeekBar = (SeekBar) view.findViewById(R.id.seek_bar_dialog_bar);
        mSeekBar.setMax(pref().getMaxValue() - pref().getMinValue());
        mSeekBar.setOnSeekBarChangeListener(this);
        mValueView = (TextView) view.findViewById(R.id.seek_bar_dialog_value);
        final int value = proxy.readValue(pref().getKey());
        mValueView.setText(proxy.getValueText(value));
        mSeekBar.setProgress(getProgressFromValue(clipValue(value)));
    }

    @Override
    protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setNeutralButton(R.string.button_default, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                final SeekBarDialogPreference pref = pref();
                final ValueProxy proxy = pref.getValueProxy();
                final String key = pref.getKey();
                proxy.writeDefaultValue(key);
                pref.setSummary(proxy.getValueText(proxy.readDefaultValue(key)));
            }
        });
    }

    @Override
    public void onDialogClosed(final boolean positiveResult) {
        if (!positiveResult) {
            return;
        }
        final SeekBarDialogPreference pref = pref();
        final ValueProxy proxy = pref.getValueProxy();
        final String key = pref.getKey();
        final int value = getClippedValueFromProgress(mSeekBar.getProgress());
        pref.setSummary(proxy.getValueText(value));
        proxy.writeValue(value, key);
    }

    @Override
    public void onProgressChanged(final SeekBar seekBar, final int progress,
            final boolean fromUser) {
        final int value = getClippedValueFromProgress(progress);
        mValueView.setText(pref().getValueProxy().getValueText(value));
        if (!fromUser) {
            mSeekBar.setProgress(getProgressFromValue(value));
        }
    }

    @Override
    public void onStartTrackingTouch(final SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(final SeekBar seekBar) {
        pref().getValueProxy().feedbackValue(getClippedValueFromProgress(seekBar.getProgress()));
    }
}
