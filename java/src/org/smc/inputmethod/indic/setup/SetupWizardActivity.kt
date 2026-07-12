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

import android.content.Context
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Message
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import com.android.inputmethod.compat.PreferenceManagerCompat
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.RichInputMethodManager
import com.android.inputmethod.latin.common.LocaleUtils
import com.android.inputmethod.latin.utils.KeyboardLanguages
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils
import com.android.inputmethod.latin.utils.TextDrawable
import com.android.inputmethod.latin.utils.UncachedInputMethodManagerUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager
import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager.Pack
import org.smc.inputmethod.indic.settings.Settings as AppSettings
import org.smc.inputmethod.indic.settings.SettingsActivity

/**
 * Modern Material 3 onboarding for the keyboard. A single adaptive screen guides the user through
 * the three Android-mandated steps — enable the IME, switch to it, then choose languages — by
 * updating its title, description and primary action per step. The underlying step state machine
 * (which system screen to open, and detecting when the user returns) is unchanged.
 */
class SetupWizardActivity : AppCompatActivity(), View.OnClickListener,
    LanguagePackDownloadManager.Listener {

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
    private lateinit var handler: SettingsPoolingHandler

    private var stepNumber = 0
    private var firstStep = 0
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

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        handler = SettingsPoolingHandler(this, imm)

        setContentView(R.layout.setup_wizard)
        setupWizard = findViewById(R.id.setup_wizard)
        logo = findViewById(R.id.setup_logo)
        welcomeWord = findViewById(R.id.setup_welcome_word)
        title = findViewById(R.id.setup_title)
        description = findViewById(R.id.setup_description)
        selectionList = findViewById(R.id.setup_selection_list)
        progress = findViewById(R.id.setup_progress)
        primaryAction = findViewById(R.id.setup_primary_action)
        primaryAction.setOnClickListener(this)
        skip = findViewById(R.id.setup_skip)
        skip.setOnClickListener(this)

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
            val carriedFirstStep = intent.getIntExtra(EXTRA_FIRST_STEP, 0)
            firstStep = if (carriedFirstStep != 0) carriedFirstStep else determineSetupStepNumber()
            stepNumber = determineSetupStepNumberFromLauncher()
        } else {
            stepNumber = savedInstanceState.getInt(STATE_STEP)
            firstStep = savedInstanceState.getInt(STATE_FIRST_STEP, STEP_1)
        }
    }

    override fun onClick(v: View?) {
        if (v === skip) {
            // Leave the downloads running in the background and move on.
            stepNumber = STEP_DONE
            updateSetupStepView()
            return
        }
        if (v !== primaryAction) {
            return
        }
        when (stepNumber) {
            STEP_WELCOME -> {
                stepNumber = determineSetupStepNumber()
                updateSetupStepView()
            }
            STEP_1 -> {
                invokeLanguageAndInputSettings()
                handler.startPollingImeSettings()
            }
            STEP_2 -> invokeInputMethodPicker()
            STEP_LANGUAGES -> {
                if (!selectionStateLoaded || selectedLocales.isEmpty()) {
                    Toast.makeText(this, R.string.su_select_a_language, Toast.LENGTH_SHORT).show()
                } else {
                    stepNumber = STEP_LAYOUTS
                    updateSetupStepView()
                }
            }
            STEP_LAYOUTS -> {
                commitSelectedLayouts()
                stepNumber = STEP_DOWNLOAD
                startPackDownloads()
                updateSetupStepView()
            }
            STEP_DOWNLOAD -> {
                stepNumber = STEP_DONE
                updateSetupStepView()
            }
            STEP_DONE -> finish()
        }
    }

    private fun invokeSetupWizardOfThisIme() {
        val intent = Intent(this, SetupWizardActivity::class.java)
        intent.setFlags(
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_SINGLE_TOP
                or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        intent.putExtra(EXTRA_FIRST_STEP, firstStep)
        startActivity(intent)
        needsToAdjustStepNumberToSystemState = true
    }

    private fun invokeSettingsOfThisIme() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.setFlags(
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        intent.putExtra(SettingsActivity.EXTRA_ENTRY_KEY, SettingsActivity.EXTRA_ENTRY_VALUE_APP_ICON)
        startActivity(intent)
    }

    private fun invokeLanguageAndInputSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_INPUT_METHOD_SETTINGS
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        startActivity(intent)
        needsToAdjustStepNumberToSystemState = true
    }

    private fun invokeInputMethodPicker() {
        imm.showInputMethodPicker()
        needsToAdjustStepNumberToSystemState = true
    }

    private fun determineSetupStepNumberFromLauncher(): Int {
        val stepNumber = determineSetupStepNumber()
        return if (stepNumber == STEP_1 || stepNumber == STEP_2) STEP_WELCOME
        else STEP_LAUNCHING_IME_SETTINGS
    }

    private fun determineSetupStepNumber(): Int {
        handler.cancelPollingImeSettings()
        if (!UncachedInputMethodManagerUtils.isThisImeEnabled(this, imm)) {
            return STEP_1
        }
        if (!UncachedInputMethodManagerUtils.isThisImeCurrent(this, imm)) {
            return STEP_2
        }
        return STEP_LANGUAGES
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_STEP, stepNumber)
        outState.putInt(STATE_FIRST_STEP, firstStep)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        stepNumber = savedInstanceState.getInt(STATE_STEP)
        firstStep = savedInstanceState.getInt(STATE_FIRST_STEP, firstStep)
    }

    override fun onRestart() {
        super.onRestart()
        if (stepNumber == STEP_1 || stepNumber == STEP_2) {
            stepNumber = determineSetupStepNumber()
        }
    }

    override fun onResume() {
        super.onResume()
        if (stepNumber == STEP_LAUNCHING_IME_SETTINGS) {
            // Prevent flashing the wizard while launching the settings activity.
            setupWizard.visibility = View.INVISIBLE
            invokeSettingsOfThisIme()
            stepNumber = STEP_BACK_FROM_IME_SETTINGS
            return
        }
        if (stepNumber == STEP_BACK_FROM_IME_SETTINGS) {
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

    private fun previousStep(step: Int): Int = when (step) {
        STEP_DONE -> STEP_DOWNLOAD
        STEP_DOWNLOAD -> STEP_LAYOUTS
        STEP_LAYOUTS -> STEP_LANGUAGES
        STEP_LANGUAGES, STEP_2, STEP_1 -> STEP_WELCOME
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
            STEP_1 -> {
                titleRes = R.string.su_step1_title
                descRes = R.string.su_step1_desc
                actionRes = R.string.su_step1_action
                showProgress = true
                showList = false
            }
            STEP_2 -> {
                titleRes = R.string.su_step2_title
                descRes = R.string.su_step2_desc
                actionRes = R.string.su_step2_action
                showProgress = true
                showList = false
            }
            STEP_LANGUAGES -> {
                titleRes = R.string.su_step3_title
                descRes = R.string.su_step3_desc
                actionRes = R.string.su_step3_action
                showProgress = true
                showList = true
            }
            STEP_LAYOUTS -> {
                titleRes = R.string.su_step4_title
                descRes = R.string.su_step4_desc
                actionRes = R.string.su_step4_action
                showProgress = true
                showList = true
            }
            STEP_DOWNLOAD -> {
                titleRes = R.string.su_step_download_title
                descRes = R.string.su_step_download_desc
                actionRes = R.string.su_done_action
                showProgress = true
                showList = true
            }
            STEP_DONE -> {
                titleRes = R.string.su_done_title
                descRes = R.string.su_done_desc
                actionRes = R.string.su_done_action
                showProgress = false
                showList = false
            }
            else -> {  // STEP_WELCOME
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
                STEP_LANGUAGES -> {
                    ensureSelectionStateLoaded()
                    buildLanguageList()
                }
                STEP_LAYOUTS -> buildLayoutList()
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
            val total = Math.max(1, STEP_DOWNLOAD - firstStep + 1)
            val current = Math.min(total, Math.max(1, stepNumber - firstStep + 1))
            progress.visibility = View.VISIBLE
            progress.text = getString(R.string.su_step_progress, current, total)
        } else {
            progress.visibility = View.GONE
        }
        // The download step blocks the primary action until packs finish, but offers Skip.
        if (stepNumber == STEP_DOWNLOAD) {
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

    private fun buildLanguageList() {
        selectionList.removeAllViews()
        for (language in sortedLanguages()) {
            val row = layoutInflater.inflate(R.layout.setup_selection_row, selectionList, false)
            (row.findViewById<ImageView>(R.id.selection_icon)).setImageDrawable(createGlyphIcon(language))
            (row.findViewById<TextView>(R.id.selection_title)).text = formatName(language)
            val toggle = row.findViewById<MaterialSwitch>(R.id.selection_switch)
            toggle.isChecked = selectedLocales.contains(language.mLocale)
            row.setOnClickListener {
                val checked = !toggle.isChecked
                toggle.isChecked = checked
                if (checked) selectedLocales.add(language.mLocale)
                else selectedLocales.remove(language.mLocale)
            }
            selectionList.addView(row)
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
                val row = layoutInflater.inflate(R.layout.setup_selection_row, selectionList, false)
                row.findViewById<View>(R.id.selection_icon).visibility = View.GONE
                (row.findViewById<TextView>(R.id.selection_title)).text = layout.mName
                val toggle = row.findViewById<MaterialSwitch>(R.id.selection_switch)
                toggle.isChecked = enabledKeys.contains(key)
                row.setOnClickListener {
                    val checked = !toggle.isChecked
                    toggle.isChecked = checked
                    if (checked) enabledKeys.add(key) else enabledKeys.remove(key)
                }
                selectionList.addView(row)
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
            val row = layoutInflater.inflate(R.layout.setup_selection_row, selectionList, false)
            row.findViewById<View>(R.id.selection_icon).visibility = View.GONE
            row.findViewById<View>(R.id.selection_switch).visibility = View.GONE
            val title = row.findViewById<TextView>(R.id.selection_title)
            title.text = downloadRowText(code)
            packRows[code] = title
            selectionList.addView(row)
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
        if (stepNumber == STEP_DOWNLOAD) {
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
        if (stepNumber == STEP_DOWNLOAD) {
            updateDownloadPrimary()
        }
    }

    override fun onInstalled(lang: String) {
        if (!packLangs.contains(lang)) return
        packPending.remove(lang)
        setPackStatus(lang, getString(R.string.su_pack_ready))
        if (stepNumber == STEP_DOWNLOAD) {
            updateDownloadPrimary()
        }
    }

    override fun onError(lang: String?, message: String?) {
        if (lang == null || !packLangs.contains(lang)) return
        packPending.remove(lang)
        setPackStatus(lang, getString(R.string.su_pack_failed))
        if (stepNumber == STEP_DOWNLOAD) {
            updateDownloadPrimary()
        }
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
        val size = Math.round(40 * resources.displayMetrics.density)
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
            if (TextUtils.isEmpty(text) || word.width == 0) {
                return
            }
            val textWidth = word.paint.measureText(text.toString())
            if (textWidth <= 0f) {
                return
            }
            val available = word.width - word.paddingLeft - word.paddingRight
            val start = word.paddingLeft + Math.max(0f, (available - textWidth) / 2f)
            word.paint.shader = LinearGradient(
                start, 0f, start + textWidth, 0f, GREETING_GRADIENT, null, Shader.TileMode.CLAMP
            )
            word.invalidate()
        }
    }

    private class SettingsPoolingHandler(
        owner: SetupWizardActivity, private val immInHandler: InputMethodManager
    ) : LeakGuardHandlerWrapper<SetupWizardActivity>(owner) {

        override fun handleMessage(msg: Message) {
            val activity = ownerInstance ?: return
            when (msg.what) {
                MSG_POLLING_IME_SETTINGS ->
                    if (UncachedInputMethodManagerUtils.isThisImeEnabled(activity, immInHandler)) {
                        activity.invokeSetupWizardOfThisIme()
                    } else {
                        startPollingImeSettings()
                    }
            }
        }

        fun startPollingImeSettings() {
            sendMessageDelayed(obtainMessage(MSG_POLLING_IME_SETTINGS), IME_SETTINGS_POLLING_INTERVAL)
        }

        fun cancelPollingImeSettings() {
            removeMessages(MSG_POLLING_IME_SETTINGS)
        }

        companion object {
            private const val MSG_POLLING_IME_SETTINGS = 0
            private const val IME_SETTINGS_POLLING_INTERVAL = 200L
        }
    }

    companion object {
        private const val EXTRA_FIRST_STEP = "first_step"
        private const val STATE_STEP = "step"
        private const val STATE_FIRST_STEP = "first_step"

        private const val STEP_WELCOME = 0
        private const val STEP_1 = 1
        private const val STEP_2 = 2
        private const val STEP_LANGUAGES = 3
        private const val STEP_LAYOUTS = 4
        private const val STEP_DOWNLOAD = 5
        private const val STEP_DONE = 6
        private const val STEP_LAUNCHING_IME_SETTINGS = 7
        private const val STEP_BACK_FROM_IME_SETTINGS = 8

        private val GREETING_GRADIENT = intArrayOf(0xFF00BFA5.toInt(), 0xFF2979FF.toInt(), 0xFF7C4DFF.toInt())
        private const val GREETING_HOLD_MS = 1500L
        private const val GREETING_FADE_MS = 450L

        private fun formatName(language: Language): CharSequence =
            if (language.mEnglishName.equals(language.mAutonym, ignoreCase = true)) {
                language.mEnglishName
            } else {
                "${language.mEnglishName} (${language.mAutonym})"
            }
    }
}
