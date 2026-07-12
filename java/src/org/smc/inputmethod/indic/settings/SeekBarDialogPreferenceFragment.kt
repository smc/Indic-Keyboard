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

package org.smc.inputmethod.indic.settings

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceDialogFragmentCompat

import com.android.inputmethod.latin.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider

import kotlin.math.roundToInt

/**
 * Dialog hosting the Material slider for [SeekBarDialogPreference]. The data and value logic live
 * on the preference; this fragment only drives the dialog UI (androidx splits the two).
 */
class SeekBarDialogPreferenceFragment : PreferenceDialogFragmentCompat() {

    private lateinit var slider: Slider
    private lateinit var valueView: TextView

    private fun pref() = preference as SeekBarDialogPreference

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Build the dialog with MaterialAlertDialogBuilder so it is a true Material 3 dialog
        // (rounded corners, MD3 buttons/typography) rather than the platform AppCompat dialog.
        // Passing this fragment as the click listener preserves the base class's positive/negative
        // result tracking that drives onDialogClosed().
        val pref = pref()
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(pref.dialogTitle)
            .setIcon(pref.dialogIcon)
            .setPositiveButton(pref.positiveButtonText, this)
            .setNegativeButton(pref.negativeButtonText, this)
        val contentView = onCreateDialogView(requireContext())
        if (contentView != null) {
            onBindDialogView(contentView)
            builder.setView(contentView)
        } else {
            builder.setMessage(pref.dialogMessage)
        }
        onPrepareDialogBuilder(builder)
        return builder.create()
    }

    private fun clipValue(value: Int): Int {
        val pref = pref()
        val clipped = value.coerceIn(pref.minValue, pref.maxValue)
        return if (pref.stepValue <= 1) clipped else clipped - (clipped % pref.stepValue)
    }

    private fun sliderValue(): Int = clipValue(slider.value.roundToInt())

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        val pref = pref()
        val proxy = pref.valueProxy!!
        valueView = view.findViewById(R.id.seek_bar_dialog_value)
        slider = view.findViewById(R.id.seek_bar_dialog_slider)
        slider.valueFrom = pref.minValue.toFloat()
        slider.valueTo = pref.maxValue.toFloat()

        val rawValue = proxy.readValue(pref.key)
        valueView.text = proxy.getValueText(rawValue)
        slider.value = clipValue(rawValue).toFloat()
        slider.setLabelFormatter { value -> proxy.getValueText(clipValue(value.roundToInt())) }
        slider.addOnChangeListener { _, value, _ ->
            valueView.text = proxy.getValueText(clipValue(value.roundToInt()))
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                proxy.feedbackValue(sliderValue())
            }
        })
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setNeutralButton(R.string.button_default) { _, _ ->
            val pref = pref()
            val proxy = pref.valueProxy!!
            val key = pref.key
            proxy.writeDefaultValue(key)
            pref.summary = proxy.getValueText(proxy.readDefaultValue(key))
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) return
        val pref = pref()
        val proxy = pref.valueProxy!!
        val value = sliderValue()
        pref.summary = proxy.getValueText(value)
        proxy.writeValue(value, pref.key)
    }

    companion object {
        // Mirrors PreferenceDialogFragmentCompat.ARG_KEY (protected in the base class).
        private const val ARG_KEY = "key"

        fun newInstance(key: String): SeekBarDialogPreferenceFragment =
            SeekBarDialogPreferenceFragment().apply {
                arguments = Bundle(1).apply { putString(ARG_KEY, key) }
            }
    }
}
