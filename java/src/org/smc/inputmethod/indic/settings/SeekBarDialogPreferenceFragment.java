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

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.android.inputmethod.latin.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import org.smc.inputmethod.indic.settings.SeekBarDialogPreference.ValueProxy;

/**
 * Dialog hosting the Material slider for {@link SeekBarDialogPreference}. The data and value logic
 * live on the preference; this fragment only drives the dialog UI (androidx splits the two).
 */
public final class SeekBarDialogPreferenceFragment extends PreferenceDialogFragmentCompat {

    private Slider mSlider;
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

    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        // Build the dialog with MaterialAlertDialogBuilder so it is a true Material 3 dialog
        // (rounded corners, MD3 buttons/typography) rather than the platform AppCompat dialog.
        // Passing this fragment as the click listener preserves the base class's positive/negative
        // result tracking that drives onDialogClosed().
        final SeekBarDialogPreference pref = pref();
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(pref.getDialogTitle())
                .setIcon(pref.getDialogIcon())
                .setPositiveButton(pref.getPositiveButtonText(), this)
                .setNegativeButton(pref.getNegativeButtonText(), this);
        final View contentView = onCreateDialogView(requireContext());
        if (contentView != null) {
            onBindDialogView(contentView);
            builder.setView(contentView);
        } else {
            builder.setMessage(pref.getDialogMessage());
        }
        onPrepareDialogBuilder(builder);
        return builder.create();
    }

    private int clipValue(final int value) {
        final SeekBarDialogPreference pref = pref();
        final int clipped = Math.min(pref.getMaxValue(), Math.max(pref.getMinValue(), value));
        if (pref.getStepValue() <= 1) {
            return clipped;
        }
        return clipped - (clipped % pref.getStepValue());
    }

    private int sliderValue() {
        return clipValue(Math.round(mSlider.getValue()));
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);
        final SeekBarDialogPreference pref = pref();
        final ValueProxy proxy = pref.getValueProxy();
        mValueView = (TextView) view.findViewById(R.id.seek_bar_dialog_value);
        mSlider = (Slider) view.findViewById(R.id.seek_bar_dialog_slider);
        mSlider.setValueFrom(pref.getMinValue());
        mSlider.setValueTo(pref.getMaxValue());

        final int rawValue = proxy.readValue(pref.getKey());
        mValueView.setText(proxy.getValueText(rawValue));
        mSlider.setValue(clipValue(rawValue));
        mSlider.setLabelFormatter(value -> proxy.getValueText(clipValue(Math.round(value))));

        mSlider.addOnChangeListener((slider, value, fromUser) ->
                mValueView.setText(proxy.getValueText(clipValue(Math.round(value)))));
        mSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(final Slider slider) {}

            @Override
            public void onStopTrackingTouch(final Slider slider) {
                proxy.feedbackValue(sliderValue());
            }
        });
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
        final int value = sliderValue();
        pref.setSummary(proxy.getValueText(value));
        proxy.writeValue(value, pref.getKey());
    }
}
