/*
 * Copyright 2026, Jishnu Mohan <jishnu@gmail.com>
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

package org.smc.inputmethod.indic.setup

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope

import com.android.inputmethod.compat.PreferenceManagerCompat
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.permissions.PermissionsManager
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils
import com.android.inputmethod.latin.utils.UncachedInputMethodManagerUtils
import com.google.android.material.button.MaterialButton

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import org.smc.inputmethod.indic.settings.Settings as AppSettings
import org.smc.inputmethod.indic.settings.SettingsActivity

/**
 * Modern Material 3 onboarding for the keyboard. A single adaptive screen guides the user through
 * enabling the IME, switching to it, choosing languages/layouts, the optional personalize step
 * and the language-pack download, by updating its title, description and primary action per step.
 * This activity owns the step state machine and lifecycle; the steps' content and state live in
 * [LanguageSelection], [SetupSelectionList], [PersonalizeStepController] and
 * [PackDownloadController].
 */
class SetupWizardActivity : AppCompatActivity() {

    /**
     * Wizard steps. [progressIndex] drives the "step N of M" indicator; the two transient states
     * that open a system screen ([LAUNCHING_IME_SETTINGS], [BACK_FROM_IME_SETTINGS]) sit outside
     * the visible progression.
     */
    private enum class Step(val progressIndex: Int) {
        WELCOME(0),
        ENABLE(1),
        SWITCH(2),
        LANGUAGES(3),
        LAYOUTS(4),
        PERSONALIZE(5),
        DOWNLOAD(6),
        DONE(7),
        LAUNCHING_IME_SETTINGS(8),
        BACK_FROM_IME_SETTINGS(9)
    }

    private lateinit var imm: InputMethodManager

    private lateinit var setupWizard: View
    private lateinit var welcomeWord: TextView
    private lateinit var title: TextView
    private lateinit var description: TextView
    private lateinit var progress: TextView
    private lateinit var primaryAction: MaterialButton
    private lateinit var logo: View
    private lateinit var skip: MaterialButton

    private lateinit var backToPreviousStep: OnBackPressedCallback
    private lateinit var greeting: GreetingAnimator
    private var pollingJob: Job? = null

    private var stepNumber = Step.WELCOME
    private var firstStep = Step.WELCOME
    private var needsToAdjustStepNumberToSystemState = false

    private val selection = LanguageSelection(this)
    private lateinit var selectionList: SetupSelectionList
    private lateinit var personalize: PersonalizeStepController
    private lateinit var packs: PackDownloadController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imm = getSystemService<InputMethodManager>()!!

        setContentView(R.layout.setup_wizard)
        setupWizard = findViewById(R.id.setup_wizard)
        logo = findViewById(R.id.setup_logo)
        welcomeWord = findViewById(R.id.setup_welcome_word)
        title = findViewById(R.id.setup_title)
        description = findViewById(R.id.setup_description)
        progress = findViewById(R.id.setup_progress)
        primaryAction = findViewById(R.id.setup_primary_action)
        primaryAction.setOnClickListener { onPrimaryAction() }
        skip = findViewById(R.id.setup_skip)
        skip.setOnClickListener { onSkip() }

        selectionList = SetupSelectionList(this, findViewById<LinearLayout>(R.id.setup_selection_list))
        personalize = PersonalizeStepController(this, selection)
        packs = PackDownloadController(this, selection) {
            if (stepNumber == Step.DOWNLOAD) {
                updateDownloadPrimary()
            }
        }

        backToPreviousStep = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                stepNumber = previousStep(stepNumber)
                updateSetupStepView()
            }
        }
        onBackPressedDispatcher.addCallback(this, backToPreviousStep)

        greeting = GreetingAnimator(welcomeWord, resources.getStringArray(R.array.su_welcome_greetings))

        ViewCompat.setOnApplyWindowInsetsListener(setupWizard) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        if (savedInstanceState == null) {
            val carriedFirstStep = stepOf(intent.getStringExtra(EXTRA_FIRST_STEP))
            firstStep = carriedFirstStep ?: determineSetupStepNumber()
            stepNumber = if (intent.getBooleanExtra(EXTRA_FORCE_SETUP, false)) {
                Step.WELCOME
            } else {
                determineSetupStepNumberFromLauncher()
            }
        } else {
            stepNumber = stepOf(savedInstanceState.getString(STATE_STEP)) ?: Step.WELCOME
            firstStep = stepOf(savedInstanceState.getString(STATE_FIRST_STEP)) ?: Step.ENABLE
        }
    }

    private fun onSkip() {
        // Leave the downloads running in the background and move on.
        stepNumber = Step.DONE
        updateSetupStepView()
    }

    private fun onPrimaryAction() {
        when (stepNumber) {
            Step.WELCOME -> {
                stepNumber = determineSetupStepNumber()
                updateSetupStepView()
            }
            Step.ENABLE -> {
                invokeLanguageAndInputSettings()
                startPollingImeSettings()
            }
            Step.SWITCH -> invokeInputMethodPicker()
            Step.LANGUAGES -> {
                if (!selection.loaded || selection.selectedLocales.isEmpty()) {
                    Toast.makeText(this, R.string.su_select_a_language, Toast.LENGTH_SHORT).show()
                } else {
                    stepNumber = Step.LAYOUTS
                    updateSetupStepView()
                }
            }
            Step.LAYOUTS -> {
                selection.commitEnabledLayouts()
                // Packs download in the background while the user is on the personalize step.
                packs.start()
                stepNumber = Step.PERSONALIZE
                updateSetupStepView()
            }
            Step.PERSONALIZE -> {
                stepNumber = Step.DOWNLOAD
                updateSetupStepView()
            }
            Step.DOWNLOAD -> {
                stepNumber = Step.DONE
                updateSetupStepView()
            }
            Step.DONE -> finish()
            else -> Unit
        }
    }

    private fun invokeSetupWizardOfThisIme() {
        val intent = Intent(this, SetupWizardActivity::class.java).apply {
            setFlags(
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(EXTRA_FIRST_STEP, firstStep.name)
        }
        startActivity(intent)
        needsToAdjustStepNumberToSystemState = true
    }

    private fun invokeSettingsOfThisIme() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(SettingsActivity.EXTRA_ENTRY_KEY, SettingsActivity.EXTRA_ENTRY_VALUE_APP_ICON)
        }
        startActivity(intent)
    }

    private fun invokeLanguageAndInputSettings() {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        startActivity(intent)
        needsToAdjustStepNumberToSystemState = true
    }

    private fun invokeInputMethodPicker() {
        imm.showInputMethodPicker()
        needsToAdjustStepNumberToSystemState = true
    }

    private fun determineSetupStepNumberFromLauncher(): Step {
        val step = determineSetupStepNumber()
        if (step == Step.ENABLE || step == Step.SWITCH) {
            // Users who already finished setup once fix this from the settings status card
            // instead of re-running onboarding.
            return if (isSetupCompleted()) Step.LAUNCHING_IME_SETTINGS else Step.WELCOME
        }
        // The IME is enabled and current, so setup is de facto done — covers users who
        // onboarded before the flag existed.
        markSetupCompleted()
        return Step.LAUNCHING_IME_SETTINGS
    }

    private fun isSetupCompleted(): Boolean =
        PreferenceManagerCompat.getDeviceSharedPreferences(this)
            .getBoolean(AppSettings.PREF_SETUP_COMPLETED, false)

    private fun markSetupCompleted() {
        PreferenceManagerCompat.getDeviceSharedPreferences(this).edit()
            .putBoolean(AppSettings.PREF_SETUP_COMPLETED, true).apply()
    }

    private fun determineSetupStepNumber(): Step {
        cancelPollingImeSettings()
        if (!UncachedInputMethodManagerUtils.isThisImeEnabled(this, imm)) {
            return Step.ENABLE
        }
        if (!UncachedInputMethodManagerUtils.isThisImeCurrent(this, imm)) {
            return Step.SWITCH
        }
        return Step.LANGUAGES
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_STEP, stepNumber.name)
        outState.putString(STATE_FIRST_STEP, firstStep.name)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        stepNumber = stepOf(savedInstanceState.getString(STATE_STEP)) ?: Step.WELCOME
        firstStep = stepOf(savedInstanceState.getString(STATE_FIRST_STEP)) ?: firstStep
    }

    override fun onRestart() {
        super.onRestart()
        if (stepNumber == Step.ENABLE || stepNumber == Step.SWITCH) {
            stepNumber = determineSetupStepNumber()
        }
    }

    override fun onResume() {
        super.onResume()
        if (stepNumber == Step.LAUNCHING_IME_SETTINGS) {
            // Prevent flashing the wizard while launching the settings activity.
            setupWizard.visibility = View.INVISIBLE
            invokeSettingsOfThisIme()
            stepNumber = Step.BACK_FROM_IME_SETTINGS
            return
        }
        if (stepNumber == Step.BACK_FROM_IME_SETTINGS) {
            finish()
            return
        }
        updateSetupStepView()
    }

    override fun onPause() {
        super.onPause()
        greeting.stop()
    }

    override fun onDestroy() {
        packs.detach()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionsManager.get(this).onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun previousStep(step: Step): Step = when (step) {
        Step.DONE -> Step.DOWNLOAD
        Step.DOWNLOAD -> Step.PERSONALIZE
        Step.PERSONALIZE -> Step.LAYOUTS
        Step.LAYOUTS -> Step.LANGUAGES
        Step.LANGUAGES, Step.SWITCH, Step.ENABLE -> Step.WELCOME
        else -> step
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && needsToAdjustStepNumberToSystemState) {
            needsToAdjustStepNumberToSystemState = false
            stepNumber = determineSetupStepNumber()
            updateSetupStepView()
        }
    }

    private fun updateSetupStepView() {
        backToPreviousStep.isEnabled = previousStep(stepNumber) != stepNumber
        setupWizard.visibility = View.VISIBLE
        val titleRes: Int
        val descRes: Int
        val actionRes: Int
        val showProgress: Boolean
        val showList: Boolean
        when (stepNumber) {
            Step.ENABLE -> {
                titleRes = R.string.su_step1_title
                descRes = R.string.su_step1_desc
                actionRes = R.string.su_step1_action
                showProgress = true
                showList = false
            }
            Step.SWITCH -> {
                titleRes = R.string.su_step2_title
                descRes = R.string.su_step2_desc
                actionRes = R.string.su_step2_action
                showProgress = true
                showList = false
            }
            Step.LANGUAGES -> {
                titleRes = R.string.su_step3_title
                descRes = R.string.su_step3_desc
                actionRes = R.string.su_step3_action
                showProgress = true
                showList = true
            }
            Step.LAYOUTS -> {
                titleRes = R.string.su_step4_title
                descRes = R.string.su_step4_desc
                actionRes = R.string.su_step4_action
                showProgress = true
                showList = true
            }
            Step.PERSONALIZE -> {
                titleRes = R.string.su_step_personalize_title
                descRes = R.string.su_step_personalize_desc
                actionRes = R.string.su_step_personalize_action
                showProgress = true
                showList = true
            }
            Step.DOWNLOAD -> {
                titleRes = R.string.su_step_download_title
                descRes = R.string.su_step_download_desc
                actionRes = R.string.su_done_action
                showProgress = true
                showList = true
            }
            Step.DONE -> {
                markSetupCompleted()
                titleRes = R.string.su_done_title
                descRes = R.string.su_done_desc
                actionRes = R.string.su_done_action
                showProgress = false
                showList = false
            }
            else -> {  // Step.WELCOME
                logo.visibility = View.VISIBLE
                selectionList.setVisible(false)
                title.visibility = View.GONE
                welcomeWord.visibility = View.VISIBLE
                description.setText(R.string.su_welcome_subtitle)
                primaryAction.setText(R.string.su_get_started)
                progress.visibility = View.GONE
                greeting.start()
                return
            }
        }
        greeting.stop()
        welcomeWord.visibility = View.GONE
        logo.visibility = if (showList) View.GONE else View.VISIBLE
        title.visibility = View.VISIBLE
        title.setText(titleRes)
        description.setText(descRes)
        primaryAction.setText(actionRes)
        selectionList.setVisible(showList)
        if (showList) {
            when (stepNumber) {
                Step.LANGUAGES -> {
                    selection.ensureLoaded()
                    buildLanguageList()
                }
                Step.LAYOUTS -> buildLayoutList()
                Step.PERSONALIZE -> personalize.buildList(selectionList)
                else -> {
                    if (!packs.started) {  // e.g. after a configuration change
                        selection.ensureLoaded()
                        packs.start()
                    }
                    packs.buildList(selectionList)
                }
            }
        }
        if (showProgress) {
            val total = (Step.DOWNLOAD.progressIndex - firstStep.progressIndex + 1).coerceAtLeast(1)
            val current = (stepNumber.progressIndex - firstStep.progressIndex + 1).coerceIn(1, total)
            progress.visibility = View.VISIBLE
            progress.text = getString(R.string.su_step_progress, current, total)
        } else {
            progress.visibility = View.GONE
        }
        // The download step blocks the primary action until packs finish, but offers Skip.
        if (stepNumber == Step.DOWNLOAD) {
            skip.visibility = View.VISIBLE
            updateDownloadPrimary()
        } else {
            skip.visibility = View.GONE
            primaryAction.isEnabled = true
        }
    }

    private fun buildLanguageList() {
        selectionList.clear()
        for (language in selection.sorted()) {
            selectionList.addRow(
                selection.formatName(language),
                icon = selectionList.glyphIcon(language),
                checked = selection.selectedLocales.contains(language.mLocale)
            ) { checked ->
                if (checked) selection.selectedLocales.add(language.mLocale)
                else selection.selectedLocales.remove(language.mLocale)
            }
        }
    }

    private fun buildLayoutList() {
        selection.reconcileEnabledWithSelection()
        selectionList.clear()
        for (language in selection.sortedSelected()) {
            selectionList.addHeader(selection.formatName(language))
            for (layout in language.mLayouts) {
                val key = SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype)
                selectionList.addLayoutRow(
                    layout.mName, layout.mSubtype, selection.enabledKeys.contains(key)
                ) { checked ->
                    if (checked) selection.enabledKeys.add(key)
                    else selection.enabledKeys.remove(key)
                }
            }
        }
    }

    private fun updateDownloadPrimary() {
        val done = packs.allDone()
        primaryAction.isEnabled = done
        primaryAction.setText(if (done) R.string.su_done_action else R.string.su_downloading_action)
    }

    // ---- IME-enabled polling ----

    /** Poll until the user enables our IME in system settings, then relaunch the wizard. */
    private fun startPollingImeSettings() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (!UncachedInputMethodManagerUtils.isThisImeEnabled(this@SetupWizardActivity, imm)) {
                delay(IME_SETTINGS_POLLING_INTERVAL)
            }
            invokeSetupWizardOfThisIme()
        }
    }

    private fun cancelPollingImeSettings() {
        pollingJob?.cancel()
        pollingJob = null
    }

    companion object {
        const val EXTRA_FORCE_SETUP = "force_setup"

        private const val EXTRA_FIRST_STEP = "first_step"
        private const val STATE_STEP = "step"
        private const val STATE_FIRST_STEP = "first_step"

        private const val IME_SETTINGS_POLLING_INTERVAL = 200L

        private fun stepOf(name: String?): Step? =
            name?.let { runCatching { Step.valueOf(it) }.getOrNull() }
    }
}
