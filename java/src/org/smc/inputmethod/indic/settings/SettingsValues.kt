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

package org.smc.inputmethod.indic.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import android.view.inputmethod.EditorInfo

import androidx.core.content.edit
import androidx.core.os.ConfigurationCompat

import com.android.inputmethod.compat.AppWorkaroundsUtils
import com.android.inputmethod.latin.InputAttributes
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.RichInputMethodManager
import com.android.inputmethod.latin.utils.AsyncResultHolder
import com.android.inputmethod.latin.utils.ResourceUtils
import com.android.inputmethod.latin.utils.TargetPackageInfoGetterTask

import java.util.Locale

/**
 * When you construct this class you may want to change the current system locale using
 * [com.android.inputmethod.latin.utils.RunInLocale].
 */
// Open (non-final) for testing via mock library.
open class SettingsValues(
    context: Context, prefs: SharedPreferences, res: Resources, inputAttributes: InputAttributes
) {
    // From resources:
    @JvmField val mSpacingAndPunctuations: SpacingAndPunctuations
    @JvmField val mDelayInMillisecondsToUpdateOldSuggestions: Int
    @JvmField val mDoubleSpacePeriodTimeout: Long
    // From configuration:
    @JvmField val mLocale: Locale
    @JvmField val mHasHardwareKeyboard: Boolean
    @JvmField val mDisplayOrientation: Int
    // From preferences, in the same order as xml/prefs.xml:
    @JvmField val mAutoCap: Boolean
    @JvmField val mVibrateOn: Boolean
    @JvmField val mSoundOn: Boolean
    @JvmField val mKeyPreviewPopupOn: Boolean
    @JvmField val mShowsVoiceInputKey: Boolean
    @JvmField val mIncludesOtherImesInLanguageSwitchList: Boolean
    @JvmField val mShowsLanguageSwitchKey: Boolean
    @JvmField val mShowsEmojiSwitchKey: Boolean
    @JvmField val mShowsNumberRow: Boolean
    @JvmField val mShowsHints: Boolean
    @JvmField val mGrayOutSuggestionsInIncognito: Boolean
    @JvmField val mUseContactsDict: Boolean
    @JvmField val mUsePersonalizedDicts: Boolean
    @JvmField val mUseDoubleSpacePeriod: Boolean
    @JvmField val mBlockPotentiallyOffensive: Boolean
    @JvmField val mSpaceTrackpadEnabled: Boolean
    @JvmField val mDeleteSwipeEnabled: Boolean
    @JvmField val mClipboardEnabled: Boolean
    @JvmField val mClipboardRecentChipEnabled: Boolean
    // Use bigrams to predict the next word when there is no input for it yet
    @JvmField val mBigramPredictionEnabled: Boolean
    // Show the raw Latin key sequence as the first suggestion on transliteration layouts
    @JvmField val mShowLatinWordSuggestion: Boolean
    @JvmField val mGestureInputEnabled: Boolean
    @JvmField val mGestureTrailEnabled: Boolean
    @JvmField val mGestureFloatingPreviewTextEnabled: Boolean
    @JvmField val mSlidingKeyInputPreviewEnabled: Boolean
    @JvmField val mKeyLongpressTimeout: Int
    @JvmField val mEnableEmojiAltPhysicalKey: Boolean
    @JvmField val mShowAppIcon: Boolean
    @JvmField val mIsShowAppIconSettingInPreferences: Boolean
    @JvmField val mCloudSyncEnabled: Boolean
    @JvmField val mEnableMetricsLogging: Boolean
    @JvmField val mShouldShowLxxSuggestionUi: Boolean
    // Use split layout for keyboard.
    @JvmField val mIsSplitKeyboardEnabled: Boolean
    @JvmField val mScreenMetrics: Int

    // From the input box
    @JvmField val mInputAttributes: InputAttributes

    // Deduced settings
    @JvmField val mKeypressVibrationDuration: Int
    @JvmField val mKeypressSoundVolume: Float
    @JvmField val mKeyPreviewPopupDismissDelay: Int
    private val mAutoCorrectEnabled: Boolean
    @JvmField val mAutoCorrectionThreshold: Float
    @JvmField val mPlausibilityThreshold: Float
    @JvmField val mAutoCorrectionEnabledPerUserSettings: Boolean
    private val mSuggestionsEnabledPerUserSettings: Boolean
    private val mAppWorkarounds: AsyncResultHolder<AppWorkaroundsUtils>

    // Debug settings
    @JvmField val mIsInternal: Boolean
    @JvmField val mHasCustomKeyPreviewAnimationParams: Boolean
    @JvmField val mHasKeyboardResize: Boolean
    @JvmField val mKeyboardHeightScale: Float
    @JvmField val mKeyPreviewShowUpDuration: Int
    @JvmField val mKeyPreviewDismissDuration: Int
    @JvmField val mKeyPreviewShowUpStartXScale: Float
    @JvmField val mKeyPreviewShowUpStartYScale: Float
    @JvmField val mKeyPreviewDismissEndXScale: Float
    @JvmField val mKeyPreviewDismissEndYScale: Float
    @JvmField val mIncognitoModeEnabled: Boolean

    @JvmField val mAccount: String?

    init {
        mLocale = ConfigurationCompat.getLocales(res.configuration).get(0) ?: Locale.getDefault()
        // Get the resources
        mDelayInMillisecondsToUpdateOldSuggestions =
            res.getInteger(R.integer.config_delay_in_milliseconds_to_update_old_suggestions)
        mSpacingAndPunctuations = SpacingAndPunctuations(res)

        // Store the input attributes
        mInputAttributes = inputAttributes

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true)
        mVibrateOn = Settings.readVibrationEnabled(prefs, res)
        mSoundOn = Settings.readKeypressSoundEnabled(prefs, res)
        mKeyPreviewPopupOn = Settings.readKeyPreviewPopupEnabled(prefs, res)
        mSlidingKeyInputPreviewEnabled =
            prefs.getBoolean(DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW, true)
        mShowsVoiceInputKey =
            needsToShowVoiceInputKey(prefs, res) && inputAttributes.mShouldShowVoiceInputKey
        mIncludesOtherImesInLanguageSwitchList =
            if (Settings.ENABLE_SHOW_LANGUAGE_SWITCH_KEY_SETTINGS)
                prefs.getBoolean(Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST, false)
            else true /* forcibly */
        mShowsLanguageSwitchKey = Settings.readShowsLanguageSwitchKey(prefs)
        mShowsEmojiSwitchKey = Settings.readShowsEmojiSwitchKey(prefs)
        mShowsNumberRow = Settings.readShowsNumberRow(prefs)
        mShowsHints = prefs.getBoolean(Settings.PREF_SHOW_HINTS, false)
        mGrayOutSuggestionsInIncognito =
            prefs.getBoolean(Settings.PREF_GRAY_OUT_SUGGESTIONS_INCOGNITO, false)
        mUseContactsDict = prefs.getBoolean(Settings.PREF_KEY_USE_CONTACTS_DICT, true)
        mUsePersonalizedDicts = prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
        mUseDoubleSpacePeriod = prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true) &&
            inputAttributes.mIsGeneralTextInput
        mBlockPotentiallyOffensive = Settings.readBlockPotentiallyOffensive(prefs, res)
        mAutoCorrectEnabled = Settings.readAutoCorrectEnabled(prefs, res)
        val autoCorrectionThresholdRawValue = if (mAutoCorrectEnabled)
            res.getString(R.string.auto_correction_threshold_mode_index_modest)
        else res.getString(R.string.auto_correction_threshold_mode_index_off)
        mBigramPredictionEnabled = readBigramPredictionEnabled(prefs, res)
        mShowLatinWordSuggestion = prefs.getBoolean(Settings.PREF_SHOW_LATIN_WORD_SUGGESTION, false)
        mDoubleSpacePeriodTimeout =
            res.getInteger(R.integer.config_double_space_period_timeout).toLong()
        mHasHardwareKeyboard = Settings.readHasHardwareKeyboard(res.configuration)
        mEnableMetricsLogging = prefs.getBoolean(Settings.PREF_ENABLE_METRICS_LOGGING, true)
        mIsSplitKeyboardEnabled = prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false)
        mScreenMetrics = Settings.readScreenMetrics(res)

        mShouldShowLxxSuggestionUi = Settings.SHOULD_SHOW_LXX_SUGGESTION_UI &&
            prefs.getBoolean(DebugSettings.PREF_SHOULD_SHOW_LXX_SUGGESTION_UI, true)
        // Compute other readable settings
        mKeyLongpressTimeout = Settings.readKeyLongpressTimeout(prefs, res)
        mKeypressVibrationDuration = Settings.readKeypressVibrationDuration(prefs, res)
        mKeypressSoundVolume = Settings.readKeypressSoundVolume(prefs, res)
        mKeyPreviewPopupDismissDelay = Settings.readKeyPreviewPopupDismissDelay(prefs, res)
        mEnableEmojiAltPhysicalKey =
            prefs.getBoolean(Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, true)
        mShowAppIcon = Settings.readShowSetupWizardIcon(prefs, context)
        mIsShowAppIconSettingInPreferences = prefs.contains(Settings.PREF_SHOW_SETUP_WIZARD_ICON)
        mAutoCorrectionThreshold =
            readAutoCorrectionThreshold(res, autoCorrectionThresholdRawValue)
        mPlausibilityThreshold = Settings.readPlausibilityThreshold(res)
        mGestureInputEnabled = Settings.readGestureInputEnabled(prefs, res)
        mGestureTrailEnabled = prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, true)
        mClipboardEnabled = Settings.readClipboardEnabled(prefs)
        mClipboardRecentChipEnabled = prefs.getBoolean(Settings.PREF_CLIPBOARD_RECENT_CHIP, true)
        mCloudSyncEnabled = prefs.getBoolean(LocalSettingsConstants.PREF_ENABLE_CLOUD_SYNC, false)
        mAccount = prefs.getString(LocalSettingsConstants.PREF_ACCOUNT_NAME, null /* default */)
        mGestureFloatingPreviewTextEnabled = !inputAttributes.mDisableGestureFloatingPreviewText &&
            prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true)
        mAutoCorrectionEnabledPerUserSettings = mAutoCorrectEnabled
        mSuggestionsEnabledPerUserSettings =
            !inputAttributes.mIsPasswordField && readSuggestionsEnabled(prefs)
        mIncognitoModeEnabled = inputAttributes.mNoLearning
        mIsInternal = Settings.isInternal(prefs)
        mHasCustomKeyPreviewAnimationParams =
            prefs.getBoolean(DebugSettings.PREF_HAS_CUSTOM_KEY_PREVIEW_ANIMATION_PARAMS, false)
        mHasKeyboardResize = prefs.getBoolean(Settings.PREF_RESIZE_KEYBOARD, false)
        mKeyboardHeightScale = Settings.readKeyboardHeight(prefs, DEFAULT_SIZE_SCALE)
        mKeyPreviewShowUpDuration = Settings.readKeyPreviewAnimationDuration(
            prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_DURATION,
            res.getInteger(R.integer.config_key_preview_show_up_duration)
        )
        mKeyPreviewDismissDuration = Settings.readKeyPreviewAnimationDuration(
            prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_DURATION,
            res.getInteger(R.integer.config_key_preview_dismiss_duration)
        )
        val defaultKeyPreviewShowUpStartScale =
            ResourceUtils.getFloatFromFraction(res, R.fraction.config_key_preview_show_up_start_scale)
        val defaultKeyPreviewDismissEndScale =
            ResourceUtils.getFloatFromFraction(res, R.fraction.config_key_preview_dismiss_end_scale)
        mKeyPreviewShowUpStartXScale = Settings.readKeyPreviewAnimationScale(
            prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_X_SCALE,
            defaultKeyPreviewShowUpStartScale
        )
        mKeyPreviewShowUpStartYScale = Settings.readKeyPreviewAnimationScale(
            prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_Y_SCALE,
            defaultKeyPreviewShowUpStartScale
        )
        mKeyPreviewDismissEndXScale = Settings.readKeyPreviewAnimationScale(
            prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_X_SCALE,
            defaultKeyPreviewDismissEndScale
        )
        mKeyPreviewDismissEndYScale = Settings.readKeyPreviewAnimationScale(
            prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_Y_SCALE,
            defaultKeyPreviewDismissEndScale
        )
        mDisplayOrientation = res.configuration.orientation
        mAppWorkarounds = AsyncResultHolder("AppWorkarounds")
        val packageInfo = TargetPackageInfoGetterTask.getCachedPackageInfo(
            mInputAttributes.mTargetApplicationPackageName
        )
        if (null != packageInfo) {
            mAppWorkarounds.set(AppWorkaroundsUtils(packageInfo))
        } else {
            fetchAppWorkarounds(context)
        }
        mSpaceTrackpadEnabled = Settings.readSpaceTrackpadEnabled(prefs)
        mDeleteSwipeEnabled = Settings.readDeleteSwipeEnabled(prefs)
    }

    // TargetPackageInfoGetterTask is an AsyncTask (a deprecated Java helper we still rely on).
    @Suppress("DEPRECATION")
    private fun fetchAppWorkarounds(context: Context) {
        TargetPackageInfoGetterTask(context, mAppWorkarounds)
            .execute(mInputAttributes.mTargetApplicationPackageName)
    }

    open fun isMetricsLoggingEnabled(): Boolean = mEnableMetricsLogging

    open fun isApplicationSpecifiedCompletionsOn(): Boolean =
        mInputAttributes.mApplicationSpecifiedCompletionOn

    open fun needsToLookupSuggestions(): Boolean =
        mInputAttributes.mShouldShowSuggestions &&
            (mAutoCorrectionEnabledPerUserSettings || isSuggestionsEnabledPerUserSettings())

    open fun isSuggestionsEnabledPerUserSettings(): Boolean = mSuggestionsEnabledPerUserSettings

    open fun isPersonalizationEnabled(): Boolean = mUsePersonalizedDicts

    open fun isWordSeparator(code: Int): Boolean = mSpacingAndPunctuations.isWordSeparator(code)

    open fun isWordConnector(code: Int): Boolean = mSpacingAndPunctuations.isWordConnector(code)

    open fun isWordCodePoint(code: Int): Boolean =
        Character.isLetter(code) || isWordConnector(code) ||
            Character.getType(code) == Character.COMBINING_SPACING_MARK.toInt()

    open fun isUsuallyPrecededBySpace(code: Int): Boolean =
        mSpacingAndPunctuations.isUsuallyPrecededBySpace(code)

    open fun isUsuallyFollowedBySpace(code: Int): Boolean =
        mSpacingAndPunctuations.isUsuallyFollowedBySpace(code)

    open fun shouldInsertSpacesAutomatically(): Boolean =
        mInputAttributes.mShouldInsertSpacesAutomatically

    open fun isLanguageSwitchKeyEnabled(): Boolean {
        if (!mShowsLanguageSwitchKey) {
            return false
        }
        val imm = RichInputMethodManager.getInstance()
        if (mIncludesOtherImesInLanguageSwitchList) {
            return imm.hasMultipleEnabledIMEsOrSubtypes(false /* include aux subtypes */)
        }
        // Layouts are self-managed (language settings pages), not system-enabled subtypes.
        return imm.getMyEnabledInputMethodSubtypeList(
            true /* allowsImplicitlySelectedSubtypes */
        ).size > 1
    }

    open fun isEmojiSwitchKeyEnabled(): Boolean = mShowsEmojiSwitchKey

    open fun isNumberRowEnabled(): Boolean = mShowsNumberRow

    open fun isSameInputType(editorInfo: EditorInfo): Boolean =
        mInputAttributes.isSameInputType(editorInfo)

    open fun hasSameOrientation(configuration: Configuration): Boolean =
        mDisplayOrientation == configuration.orientation

    open fun isBeforeJellyBean(): Boolean {
        val appWorkaroundUtils = mAppWorkarounds.get(null, TIMEOUT_TO_GET_TARGET_PACKAGE.toLong())
        return appWorkaroundUtils?.isBeforeJellyBean ?: false
    }

    open fun isBrokenByRecorrection(): Boolean {
        val appWorkaroundUtils = mAppWorkarounds.get(null, TIMEOUT_TO_GET_TARGET_PACKAGE.toLong())
        return appWorkaroundUtils?.isBrokenByRecorrection ?: false
    }

    open fun dump(): String = buildString {
        append("Current settings :")
        append("\n   mSpacingAndPunctuations = ${mSpacingAndPunctuations.dump()}")
        append("\n   mDelayInMillisecondsToUpdateOldSuggestions = $mDelayInMillisecondsToUpdateOldSuggestions")
        append("\n   mAutoCap = $mAutoCap")
        append("\n   mVibrateOn = $mVibrateOn")
        append("\n   mSoundOn = $mSoundOn")
        append("\n   mKeyPreviewPopupOn = $mKeyPreviewPopupOn")
        append("\n   mShowsVoiceInputKey = $mShowsVoiceInputKey")
        append("\n   mIncludesOtherImesInLanguageSwitchList = $mIncludesOtherImesInLanguageSwitchList")
        append("\n   mShowsLanguageSwitchKey = $mShowsLanguageSwitchKey")
        append("\n   mUseContactsDict = $mUseContactsDict")
        append("\n   mUsePersonalizedDicts = $mUsePersonalizedDicts")
        append("\n   mUseDoubleSpacePeriod = $mUseDoubleSpacePeriod")
        append("\n   mBlockPotentiallyOffensive = $mBlockPotentiallyOffensive")
        append("\n   mBigramPredictionEnabled = $mBigramPredictionEnabled")
        append("\n   mGestureInputEnabled = $mGestureInputEnabled")
        append("\n   mGestureTrailEnabled = $mGestureTrailEnabled")
        append("\n   mGestureFloatingPreviewTextEnabled = $mGestureFloatingPreviewTextEnabled")
        append("\n   mSlidingKeyInputPreviewEnabled = $mSlidingKeyInputPreviewEnabled")
        append("\n   mKeyLongpressTimeout = $mKeyLongpressTimeout")
        append("\n   mLocale = $mLocale")
        append("\n   mInputAttributes = $mInputAttributes")
        append("\n   mKeypressVibrationDuration = $mKeypressVibrationDuration")
        append("\n   mKeypressSoundVolume = $mKeypressSoundVolume")
        append("\n   mKeyPreviewPopupDismissDelay = $mKeyPreviewPopupDismissDelay")
        append("\n   mAutoCorrectEnabled = $mAutoCorrectEnabled")
        append("\n   mAutoCorrectionThreshold = $mAutoCorrectionThreshold")
        append("\n   mAutoCorrectionEnabledPerUserSettings = $mAutoCorrectionEnabledPerUserSettings")
        append("\n   mSuggestionsEnabledPerUserSettings = $mSuggestionsEnabledPerUserSettings")
        append("\n   mDisplayOrientation = $mDisplayOrientation")
        append("\n   mAppWorkarounds = ${mAppWorkarounds.get(null, 0L)?.toString() ?: "null"}")
        append("\n   mIsInternal = $mIsInternal")
        append("\n   mKeyPreviewShowUpDuration = $mKeyPreviewShowUpDuration")
        append("\n   mKeyPreviewDismissDuration = $mKeyPreviewDismissDuration")
        append("\n   mKeyPreviewShowUpStartScaleX = $mKeyPreviewShowUpStartXScale")
        append("\n   mKeyPreviewShowUpStartScaleY = $mKeyPreviewShowUpStartYScale")
        append("\n   mKeyPreviewDismissEndScaleX = $mKeyPreviewDismissEndXScale")
        append("\n   mKeyPreviewDismissEndScaleY = $mKeyPreviewDismissEndYScale")
    }

    companion object {
        private val TAG = SettingsValues::class.java.simpleName

        // "floatMaxValue" and "floatNegativeInfinity" are special marker strings for
        // Float.MAX_VALUE and Float.NEGATIVE_INFINITY, used for auto-correction settings.
        private const val FLOAT_MAX_VALUE_MARKER_STRING = "floatMaxValue"
        private const val FLOAT_NEGATIVE_INFINITY_MARKER_STRING = "floatNegativeInfinity"
        private const val TIMEOUT_TO_GET_TARGET_PACKAGE = 5 // seconds

        const val DEFAULT_SIZE_SCALE = 1.0f // 100%

        private const val SUGGESTIONS_VISIBILITY_HIDE_VALUE_OBSOLETE = "2"

        private fun readSuggestionsEnabled(prefs: SharedPreferences): Boolean {
            if (prefs.contains(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE)) {
                val alwaysHide = SUGGESTIONS_VISIBILITY_HIDE_VALUE_OBSOLETE ==
                    prefs.getString(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE, null)
                prefs.edit {
                    remove(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE)
                    putBoolean(Settings.PREF_SHOW_SUGGESTIONS, !alwaysHide)
                }
            }
            return prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
        }

        private fun readBigramPredictionEnabled(
            prefs: SharedPreferences, res: Resources
        ): Boolean = prefs.getBoolean(
            Settings.PREF_BIGRAM_PREDICTIONS,
            res.getBoolean(R.bool.config_default_next_word_prediction)
        )

        private fun readAutoCorrectionThreshold(
            res: Resources, currentAutoCorrectionSetting: String
        ): Float {
            val autoCorrectionThresholdValues =
                res.getStringArray(R.array.auto_correction_threshold_values)
            // When autoCorrectionThreshold is greater than 1.0, it's like auto correction is off.
            return try {
                val arrayIndex = currentAutoCorrectionSetting.toInt()
                if (arrayIndex >= 0 && arrayIndex < autoCorrectionThresholdValues.size) {
                    when (val v = autoCorrectionThresholdValues[arrayIndex]) {
                        FLOAT_MAX_VALUE_MARKER_STRING -> Float.MAX_VALUE
                        FLOAT_NEGATIVE_INFINITY_MARKER_STRING -> Float.NEGATIVE_INFINITY
                        else -> v.toFloat()
                    }
                } else {
                    Float.MAX_VALUE
                }
            } catch (e: NumberFormatException) {
                // Whenever the threshold settings are correct, never come here.
                Log.w(
                    TAG, "Cannot load auto correction threshold setting." +
                        " currentAutoCorrectionSetting: $currentAutoCorrectionSetting" +
                        ", autoCorrectionThresholdValues: " +
                        autoCorrectionThresholdValues.contentToString(), e
                )
                Float.MAX_VALUE
            }
        }

        private fun needsToShowVoiceInputKey(
            prefs: SharedPreferences, res: Resources
        ): Boolean {
            // Migrate preference from Settings.PREF_VOICE_MODE_OBSOLETE to
            // Settings.PREF_VOICE_INPUT_KEY.
            if (prefs.contains(Settings.PREF_VOICE_MODE_OBSOLETE)) {
                val voiceModeMain = res.getString(R.string.voice_mode_main)
                val voiceMode = prefs.getString(Settings.PREF_VOICE_MODE_OBSOLETE, voiceModeMain)
                val shouldShowVoiceInputKey = voiceModeMain == voiceMode
                prefs.edit {
                    putBoolean(Settings.PREF_VOICE_INPUT_KEY, shouldShowVoiceInputKey)
                    remove(Settings.PREF_VOICE_MODE_OBSOLETE)
                }
            }
            return prefs.getBoolean(Settings.PREF_VOICE_INPUT_KEY, false)
        }
    }
}
