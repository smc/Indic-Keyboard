/*
 * Copyright 2026, Jishnu Mohan <jishnu7@gmail.com>
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

import android.content.Context
import android.content.Intent
import android.provider.Settings as SystemSettings
import android.util.AttributeSet
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView

import androidx.core.content.getSystemService
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.UncachedInputMethodManagerUtils

import org.smc.inputmethod.indic.setup.SetupWizardActivity

/**
 * Status card pinned above the settings list whenever this keyboard is not the current input method
 */
class SetupStatusPreference(context: Context, attrs: AttributeSet?) :
    Preference(context, attrs), SelfContainedCard {

    private enum class State { HIDDEN, SWITCHED_AWAY, DISABLED }

    private var state = State.HIDDEN

    init {
        layoutResource = R.layout.setup_status_banner
        isSelectable = false
        isIconSpaceReserved = false
        isPersistent = false
        isVisible = false
    }

    fun refresh() {
        val imm = context.getSystemService<InputMethodManager>()
        val newState = when {
            imm == null -> State.HIDDEN
            !UncachedInputMethodManagerUtils.isThisImeEnabled(context, imm) -> State.DISABLED
            !UncachedInputMethodManagerUtils.isThisImeCurrent(context, imm) -> State.SWITCHED_AWAY
            else -> State.HIDDEN
        }
        if (newState == state) {
            return
        }
        state = newState
        isVisible = state != State.HIDDEN
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        val title = holder.findViewById(R.id.setup_banner_title) as TextView
        val body = holder.findViewById(R.id.setup_banner_body) as TextView
        val primary = holder.findViewById(R.id.setup_banner_primary) as Button
        val secondary = holder.findViewById(R.id.setup_banner_secondary) as Button
        if (state == State.DISABLED) {
            title.text = context.getString(
                R.string.setup_banner_disabled_title, context.getString(R.string.english_ime_name)
            )
            body.setText(R.string.setup_banner_disabled_body)
            primary.setText(R.string.setup_banner_turn_on)
            primary.setOnClickListener {
                context.startActivity(
                    Intent(SystemSettings.ACTION_INPUT_METHOD_SETTINGS)
                        .addCategory(Intent.CATEGORY_DEFAULT)
                )
            }
        } else {
            title.setText(R.string.setup_banner_switched_title)
            val label = currentImeLabel()
            body.text = if (label != null) {
                context.getString(R.string.setup_banner_switched_body, label)
            } else {
                context.getString(R.string.setup_banner_switched_body_generic)
            }
            primary.setText(R.string.setup_banner_switch_back)
            primary.setOnClickListener {
                context.getSystemService<InputMethodManager>()?.showInputMethodPicker()
            }
        }
        secondary.setOnClickListener {
            context.startActivity(
                Intent(context, SetupWizardActivity::class.java)
                    .putExtra(SetupWizardActivity.EXTRA_FORCE_SETUP, true)
            )
        }
    }

    private fun currentImeLabel(): CharSequence? {
        val imm = context.getSystemService<InputMethodManager>() ?: return null
        val currentId = SystemSettings.Secure.getString(
            context.contentResolver, SystemSettings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return null
        return imm.enabledInputMethodList.firstOrNull { it.id == currentId }
            ?.loadLabel(context.packageManager)
    }
}
