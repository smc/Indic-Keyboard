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
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
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
import com.android.inputmethod.latin.RichInputMethodManager
import com.android.inputmethod.latin.common.LocaleUtils
import com.android.inputmethod.latin.utils.KeyboardLanguages
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils
import com.android.inputmethod.latin.utils.TextDrawable
import com.android.inputmethod.latin.utils.UncachedInputMethodManagerUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager
import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager.Pack
import org.smc.inputmethod.indic.settings.Settings as AppSettings
import org.smc.inputmethod.indic.settings.SettingsActivity

import kotlin.math.roundToInt

/**
 * Modern Material 3 onboarding for the keyboard. A single adaptive screen guides the user through
 * the three Android-mandated steps — enable the IME, switch to it, then choose languages — by
 * updating its title, description and primary action per step. The underlying step state machine
 * (which system screen to open, and detecting when the user returns) is unchanged.
 */
class SetupWizardActivity : AppCompatActivity(), LanguagePackDownloadManager.Listener {

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
        DOWNLOAD(5),
        DONE(6),
        LAUNCHING_IME_SETTINGS(7),
        BACK_FROM_IME_SETTINGS(8)
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
    private lateinit var selectionList: LinearLayout

    private lateinit var backToPreviousStep: OnBackPressedCallback
    private lateinit var greeting: GreetingAnimator
    private var pollingJob: Job? = null

    private var stepNumber = Step.WELCOME
    private var firstStep = Step.WELCOME
    private var needsToAdjustStepNumberToSystemState = false

    private var selectionStateLoaded = false
    private lateinit var languages: List<Language>
    private lateinit var selectedLocales: MutableSet<String>
    private lateinit var enabledKeys: MutableSet<String>

    // Language-pack download step state.
    private var packManager: LanguagePackDownloadManager? = null
    private val packLangs = ArrayList<String>()              // language codes to fetch
    private val packPending = HashSet<String>()              // still downloading
    private val packStatus = HashMap<String, CharSequence>()
    private val packRows = HashMap<String, TextView>()
    private var packStarted = false
    private var packIndexLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imm = getSystemService<InputMethodManager>()!!

        setContentView(R.layout.setup_wizard)
        setupWizard = findViewById(R.id.setup_wizard)
        logo = findViewById(R.id.setup_logo)
        welcomeWord = findViewById(R.id.setup_welcome_word)
        title = findViewById(R.id.setup_title)
        description = findViewById(R.id.setup_description)
        selectionList = findViewById(R.id.setup_selection_list)
        progress = findViewById(R.id.setup_progress)
        primaryAction = findViewById(R.id.setup_primary_action)
        primaryAction.setOnClickListener { onPrimaryAction() }
        skip = findViewById(R.id.setup_skip)
        skip.setOnClickListener { onSkip() }

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
            stepNumber = determineSetupStepNumberFromLauncher()
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
                if (!selectionStateLoaded || selectedLocales.isEmpty()) {
                    Toast.makeText(this, R.string.su_select_a_language, Toast.LENGTH_SHORT).show()
                } else {
                    stepNumber = Step.LAYOUTS
                    updateSetupStepView()
                }
            }
            Step.LAYOUTS -> {
                commitSelectedLayouts()
                stepNumber = Step.DOWNLOAD
                startPackDownloads()
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
        return if (step == Step.ENABLE || step == Step.SWITCH) Step.WELCOME
        else Step.LAUNCHING_IME_SETTINGS
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
        packManager?.setListener(null)  // downloads continue in the system DownloadManager
        super.onDestroy()
    }

    private fun previousStep(step: Step): Step = when (step) {
        Step.DONE -> Step.DOWNLOAD
        Step.DOWNLOAD -> Step.LAYOUTS
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
            Step.DOWNLOAD -> {
                titleRes = R.string.su_step_download_title
                descRes = R.string.su_step_download_desc
                actionRes = R.string.su_done_action
                showProgress = true
                showList = true
            }
            Step.DONE -> {
                titleRes = R.string.su_done_title
                descRes = R.string.su_done_desc
                actionRes = R.string.su_done_action
                showProgress = false
                showList = false
            }
            else -> {  // Step.WELCOME
                logo.visibility = View.VISIBLE
                selectionList.visibility = View.GONE
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
        if (showList) {
            selectionList.visibility = View.VISIBLE
            when (stepNumber) {
                Step.LANGUAGES -> {
                    ensureSelectionStateLoaded()
                    buildLanguageList()
                }
                Step.LAYOUTS -> buildLayoutList()
                else -> {
                    if (!packStarted) {  // e.g. after a configuration change
                        ensureSelectionStateLoaded()
                        startPackDownloads()
                    }
                    buildDownloadList()
                }
            }
        } else {
            selectionList.visibility = View.GONE
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

    private fun ensureSelectionStateLoaded() {
        if (selectionStateLoaded) {
            return
        }
        selectionStateLoaded = true
        RichInputMethodManager.init(this)
        languages = KeyboardLanguages.getLanguages(this)
        enabledKeys = HashSet(
            AppSettings.readEnabledSubtypeKeys(PreferenceManagerCompat.getDeviceSharedPreferences(this))
        )
        selectedLocales = LinkedHashSet()
        for (language in languages) {
            for (layout in language.mLayouts) {
                if (enabledKeys.contains(SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype))) {
                    selectedLocales.add(language.mLocale)
                    break
                }
            }
        }
    }

    /** Inflate one selection row and add it to the list, wiring its toggle when shown. */
    private fun addSelectionRow(
        rowTitle: CharSequence,
        icon: Drawable? = null,
        showSwitch: Boolean = true,
        checked: Boolean = false,
        onToggle: (Boolean) -> Unit = {}
    ): View {
        val row = layoutInflater.inflate(R.layout.setup_selection_row, selectionList, false)
        val iconView = row.findViewById<ImageView>(R.id.selection_icon)
        if (icon != null) iconView.setImageDrawable(icon) else iconView.visibility = View.GONE
        row.findViewById<TextView>(R.id.selection_title).text = rowTitle
        val toggle = row.findViewById<MaterialSwitch>(R.id.selection_switch)
        if (showSwitch) {
            toggle.isChecked = checked
            row.setOnClickListener {
                val nowChecked = !toggle.isChecked
                toggle.isChecked = nowChecked
                onToggle(nowChecked)
            }
        } else {
            toggle.visibility = View.GONE
        }
        selectionList.addView(row)
        return row
    }

    private fun buildLanguageList() {
        selectionList.removeAllViews()
        for (language in sortedLanguages()) {
            addSelectionRow(
                formatName(language),
                icon = createGlyphIcon(language),
                checked = selectedLocales.contains(language.mLocale)
            ) { checked ->
                if (checked) selectedLocales.add(language.mLocale)
                else selectedLocales.remove(language.mLocale)
            }
        }
    }

    private fun buildLayoutList() {
        reconcileEnabledWithSelection()
        selectionList.removeAllViews()
        for (language in sortedLanguages()) {
            if (!selectedLocales.contains(language.mLocale)) {
                continue
            }
            val header = layoutInflater.inflate(
                R.layout.setup_selection_header, selectionList, false
            ) as TextView
            header.text = formatName(language)
            selectionList.addView(header)
            for (layout in language.mLayouts) {
                val key = SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype)
                addSelectionRow(layout.mName, checked = enabledKeys.contains(key)) { checked ->
                    if (checked) enabledKeys.add(key) else enabledKeys.remove(key)
                }
            }
        }
    }

    private fun reconcileEnabledWithSelection() {
        for (language in languages) {
            val selected = selectedLocales.contains(language.mLocale)
            val hasEnabled = language.mLayouts.any {
                enabledKeys.contains(SubtypeLocaleUtils.getSubtypeKey(it.mSubtype))
            }
            if (selected && !hasEnabled && language.mLayouts.isNotEmpty()) {
                enabledKeys.add(SubtypeLocaleUtils.getSubtypeKey(language.mLayouts[0].mSubtype))
            } else if (!selected && hasEnabled) {
                for (layout in language.mLayouts) {
                    enabledKeys.remove(SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype))
                }
            }
        }
    }

    private fun commitSelectedLayouts() {
        reconcileEnabledWithSelection()
        RichInputMethodManager.init(this)
        RichInputMethodManager.getInstance().setEnabledSubtypeKeys(enabledKeys)
    }

    // ---- Language-pack download step ----

    private fun startPackDownloads() {
        if (packStarted) {
            return
        }
        packStarted = true
        packLangs.clear()
        for (language in languages) {
            if (!selectedLocales.contains(language.mLocale)) {
                continue
            }
            val code = LocaleUtils.constructLocaleFromString(language.mLocale).language
            // English ships with the app and has no downloadable pack — don't list it.
            if ("en" == code || packLangs.contains(code)) {
                continue
            }
            packLangs.add(code)
            packStatus[code] = getString(R.string.su_pack_waiting)
        }
        if (packLangs.isEmpty()) {
            packIndexLoaded = true
            return
        }
        packManager = LanguagePackDownloadManager(this).also {
            it.setListener(this)
            it.loadIndex()  // schemes arrive in onIndexLoaded, then we download
        }
    }

    private fun buildDownloadList() {
        selectionList.removeAllViews()
        packRows.clear()
        for (code in packLangs) {
            val row = addSelectionRow(downloadRowText(code), showSwitch = false)
            packRows[code] = row.findViewById(R.id.selection_title)
        }
    }

    private fun downloadRowText(code: String): CharSequence =
        "${langDisplayName(code)} · ${packStatus[code] ?: ""}"

    private fun langDisplayName(code: String): CharSequence {
        for (language in languages) {
            if (code == LocaleUtils.constructLocaleFromString(language.mLocale).language) {
                return language.mEnglishName
            }
        }
        return code
    }

    private fun setPackStatus(code: String, status: CharSequence) {
        packStatus[code] = status
        packRows[code]?.text = downloadRowText(code)
    }

    private fun allPacksDone(): Boolean = packIndexLoaded && packPending.isEmpty()

    private fun updateDownloadPrimary() {
        val done = allPacksDone()
        primaryAction.isEnabled = done
        primaryAction.setText(if (done) R.string.su_done_action else R.string.su_downloading_action)
    }

    // ---- LanguagePackDownloadManager.Listener ----

    override fun onIndexLoaded(schemes: List<Pack>) {
        packIndexLoaded = true
        for (code in packLangs) {
            val pack = schemes.firstOrNull { code == it.lang }
            when {
                pack == null -> setPackStatus(code, getString(R.string.su_pack_unavailable))
                packManager!!.ensureDownloaded(pack) -> {
                    packPending.add(code)
                    setPackStatus(code, getString(R.string.su_pack_waiting))
                }
                else -> setPackStatus(code, getString(R.string.su_pack_ready))
            }
        }
        if (stepNumber == Step.DOWNLOAD) {
            updateDownloadPrimary()
        }
    }

    override fun onProgress(lang: String, percent: Int) {
        if (!packLangs.contains(lang)) return
        packPending.add(lang)
        if (percent == LanguagePackDownloadManager.INSTALLING) {
            setPackStatus(lang, getString(R.string.language_pack_installing))
        } else {
            setPackStatus(lang, getString(R.string.language_pack_downloading, percent))
        }
        if (stepNumber == Step.DOWNLOAD) {
            updateDownloadPrimary()
        }
    }

    override fun onInstalled(lang: String) {
        if (!packLangs.contains(lang)) return
        packPending.remove(lang)
        setPackStatus(lang, getString(R.string.su_pack_ready))
        if (stepNumber == Step.DOWNLOAD) {
            updateDownloadPrimary()
        }
    }

    override fun onError(lang: String?, message: String?) {
        if (lang == null || !packLangs.contains(lang)) return
        packPending.remove(lang)
        setPackStatus(lang, getString(R.string.su_pack_failed))
        if (stepNumber == Step.DOWNLOAD) {
            updateDownloadPrimary()
        }
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

    private fun sortedLanguages(): List<Language> = languages.sortedWith { a, b ->
        val aEn = a.mLocale.startsWith("en")
        val bEn = b.mLocale.startsWith("en")
        when {
            aEn != bEn -> if (aEn) -1 else 1
            else -> a.mEnglishName.compareTo(b.mEnglishName, ignoreCase = true)
        }
    }

    private fun createGlyphIcon(language: Language): Drawable {
        val value = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true)
        val size = (40 * resources.displayMetrics.density).roundToInt()
        return TextDrawable(language.mGlyph, value.data, 0 /* no background */, size)
    }

    /** Cross-fades through the localized greetings on the welcome step, painting each with a
     *  horizontal gradient sized to the rendered text. */
    private class GreetingAnimator(
        private val word: TextView, private val greetings: Array<String>
    ) {
        private var index = 0
        private val cycler = object : Runnable {
            override fun run() {
                index = (index + 1) % greetings.size
                crossFadeTo(index)
                word.postDelayed(this, GREETING_HOLD_MS + GREETING_FADE_MS * 2)
            }
        }

        init {
            word.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyGradient() }
        }

        fun start() {
            index = 0
            word.animate().cancel()
            word.alpha = 1f
            word.text = greetings[0]
            applyGradient()
            word.removeCallbacks(cycler)
            if (greetings.size > 1) {
                word.postDelayed(cycler, GREETING_HOLD_MS + GREETING_FADE_MS)
            }
        }

        fun stop() {
            word.removeCallbacks(cycler)
            word.animate().cancel()
        }

        private fun crossFadeTo(i: Int) {
            word.animate().alpha(0f).setDuration(GREETING_FADE_MS).withEndAction {
                word.text = greetings[i]
                applyGradient()
                word.animate().alpha(1f).setDuration(GREETING_FADE_MS).start()
            }.start()
        }

        private fun applyGradient() {
            val text = word.text
            if (text.isNullOrEmpty() || word.width == 0) {
                return
            }
            val textWidth = word.paint.measureText(text.toString())
            if (textWidth <= 0f) {
                return
            }
            val available = word.width - word.paddingLeft - word.paddingRight
            val start = word.paddingLeft + ((available - textWidth) / 2f).coerceAtLeast(0f)
            word.paint.shader = LinearGradient(
                start, 0f, start + textWidth, 0f, GREETING_GRADIENT, null, Shader.TileMode.CLAMP
            )
            word.invalidate()
        }
    }

    companion object {
        private const val EXTRA_FIRST_STEP = "first_step"
        private const val STATE_STEP = "step"
        private const val STATE_FIRST_STEP = "first_step"

        private const val IME_SETTINGS_POLLING_INTERVAL = 200L

        private val GREETING_GRADIENT = intArrayOf(0xFF00BFA5.toInt(), 0xFF2979FF.toInt(), 0xFF7C4DFF.toInt())
        private const val GREETING_HOLD_MS = 1500L
        private const val GREETING_FADE_MS = 450L

        private fun stepOf(name: String?): Step? =
            name?.let { runCatching { Step.valueOf(it) }.getOrNull() }

        private fun formatName(language: Language): CharSequence =
            if (language.mEnglishName.equals(language.mAutonym, ignoreCase = true)) {
                language.mEnglishName
            } else {
                "${language.mEnglishName} (${language.mAutonym})"
            }
    }
}
