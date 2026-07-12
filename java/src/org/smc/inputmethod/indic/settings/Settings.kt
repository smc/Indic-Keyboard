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
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.text.TextUtils
import android.util.Log

import androidx.core.content.edit

import com.android.inputmethod.compat.PreferenceManagerCompat
import com.android.inputmethod.latin.AudioAndHapticFeedbackManager
import com.android.inputmethod.latin.InputAttributes
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.utils.AdditionalSubtypeUtils
import com.android.inputmethod.latin.utils.JsonUtils
import com.android.inputmethod.latin.utils.ResourceUtils
import com.android.inputmethod.latin.utils.RunInLocale
import com.android.inputmethod.latin.utils.StatsUtils

import java.util.Locale
import java.util.concurrent.locks.ReentrantLock

class Settings private constructor() : SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var mContext: Context
    private lateinit var mRes: Resources
    private lateinit var mPrefs: SharedPreferences
    private var mSettingsValues: SettingsValues? = null
    private val mSettingsValuesLock = ReentrantLock()

    private fun onCreate(context: Context) {
        mContext = context
        mRes = context.resources
        mPrefs = PreferenceManagerCompat.getDeviceSharedPreferences(context)
        mPrefs.registerOnSharedPreferenceChangeListener(this)
        upgradeAutocorrectionSettings(mPrefs, mRes)
    }

    fun onDestroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        mSettingsValuesLock.lock()
        try {
            val current = mSettingsValues
            if (current == null) {
                // TODO: Introduce a static function to register this class and ensure that
                // loadSettings must be called before onSharedPreferenceChanged is called.
                Log.w(TAG, "onSharedPreferenceChanged called before loadSettings.")
                return
            }
            loadSettings(mContext, current.mLocale, current.mInputAttributes)
            StatsUtils.onLoadSettings(mSettingsValues)
        } finally {
            mSettingsValuesLock.unlock()
        }
    }

    fun loadSettings(context: Context, locale: Locale, inputAttributes: InputAttributes) {
        mSettingsValuesLock.lock()
        mContext = context
        try {
            val prefs = mPrefs
            val job = object : RunInLocale<SettingsValues>() {
                override fun job(res: Resources): SettingsValues =
                    SettingsValues(context, prefs, res, inputAttributes)
            }
            mSettingsValues = job.runInLocale(mRes, locale)
        } finally {
            mSettingsValuesLock.unlock()
        }
    }

    // TODO: Remove this method and add proxy method to SettingsValues.
    fun getCurrent(): SettingsValues? = mSettingsValues

    private fun upgradeAutocorrectionSettings(prefs: SharedPreferences, res: Resources) {
        val thresholdSetting = prefs.getString(PREF_AUTO_CORRECTION_THRESHOLD_OBSOLETE, null)
            ?: return
        val autoCorrectionOff = res.getString(R.string.auto_correction_threshold_mode_index_off)
        prefs.edit(commit = true) {
            remove(PREF_AUTO_CORRECTION_THRESHOLD_OBSOLETE)
            putBoolean(PREF_AUTO_CORRECTION, thresholdSetting != autoCorrectionOff)
        }
    }

    companion object {
        private val TAG = Settings::class.java.simpleName

        // Settings screens
        const val SCREEN_ACCOUNTS = "screen_accounts"
        const val SCREEN_THEME = "screen_theme"
        const val SCREEN_DEBUG = "screen_debug"

        // In the same order as xml/prefs.xml
        const val PREF_AUTO_CAP = "auto_cap"
        const val PREF_VIBRATE_ON = "vibrate_on"
        const val PREF_SOUND_ON = "sound_on"
        const val PREF_POPUP_ON = "popup_on"
        // PREF_VOICE_MODE_OBSOLETE is obsolete. Use PREF_VOICE_INPUT_KEY instead.
        const val PREF_VOICE_MODE_OBSOLETE = "voice_mode"
        const val PREF_VOICE_INPUT_KEY = "pref_voice_input_key"
        const val PREF_EDIT_PERSONAL_DICTIONARY = "edit_personal_dictionary"
        const val PREF_CONFIGURE_DICTIONARIES_KEY = "configure_dictionaries_key"
        // PREF_AUTO_CORRECTION_THRESHOLD_OBSOLETE is obsolete. Use PREF_AUTO_CORRECTION instead.
        const val PREF_AUTO_CORRECTION_THRESHOLD_OBSOLETE = "auto_correction_threshold"
        const val PREF_AUTO_CORRECTION = "pref_key_auto_correction"
        // PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE is obsolete. Use PREF_SHOW_SUGGESTIONS instead.
        const val PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE = "show_suggestions_setting"
        const val PREF_SHOW_SUGGESTIONS = "show_suggestions"
        const val PREF_KEY_USE_CONTACTS_DICT = "pref_key_use_contacts_dict"
        const val PREF_KEY_USE_PERSONALIZED_DICTS = "pref_key_use_personalized_dicts"
        const val PREF_KEY_USE_DOUBLE_SPACE_PERIOD = "pref_key_use_double_space_period"
        const val PREF_BLOCK_POTENTIALLY_OFFENSIVE = "pref_key_block_potentially_offensive"
        const val ENABLE_SHOW_LANGUAGE_SWITCH_KEY_SETTINGS = true
        const val SHOULD_SHOW_LXX_SUGGESTION_UI = true
        const val PREF_SHOW_LANGUAGE_SWITCH_KEY = "pref_show_language_switch_key"
        const val PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST =
            "pref_include_other_imes_in_language_switch_list"
        const val PREF_CUSTOM_INPUT_STYLES = "custom_input_styles"
        const val PREF_ENABLED_SUBTYPES = "enabled_subtypes"
        const val PREF_ENABLE_SPLIT_KEYBOARD = "pref_split_keyboard"
        // TODO: consolidate key preview dismiss delay with the key preview animation parameters.
        const val PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY = "pref_key_preview_popup_dismiss_delay"
        const val PREF_BIGRAM_PREDICTIONS = "next_word_prediction"
        const val PREF_SHOW_LATIN_WORD_SUGGESTION = "pref_show_latin_word_suggestion"
        const val PREF_GESTURE_INPUT = "gesture_input"
        const val PREF_VIBRATION_DURATION_SETTINGS = "pref_vibration_duration_settings"
        const val PREF_KEYPRESS_SOUND_VOLUME = "pref_keypress_sound_volume"
        const val PREF_KEY_LONGPRESS_TIMEOUT = "pref_key_longpress_timeout"
        const val PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY = "pref_enable_emoji_alt_physical_key"
        const val PREF_GESTURE_PREVIEW_TRAIL = "pref_gesture_preview_trail"
        const val PREF_GESTURE_FLOATING_PREVIEW_TEXT = "pref_gesture_floating_preview_text"
        const val PREF_SHOW_SETUP_WIZARD_ICON = "pref_show_setup_wizard_icon"

        const val PREF_KEY_IS_INTERNAL = "pref_key_is_internal"

        const val PREF_ENABLE_METRICS_LOGGING = "pref_enable_metrics_logging"
        // Deprecated. Use PREF_SHOW_LANGUAGE_SWITCH_KEY instead; kept for backward compatibility.
        private const val PREF_SUPPRESS_LANGUAGE_SWITCH_KEY = "pref_suppress_language_switch_key"
        private const val PREF_SHOW_EMOJI_SWITCH_KEY = "pref_show_emoji_switch_key"
        private const val PREF_SHOW_NUMBER_ROW = "pref_show_number_row"
        const val PREF_SHOW_HINTS = "pref_show_hints"
        const val PREF_GRAY_OUT_SUGGESTIONS_INCOGNITO = "pref_gray_out_suggestions_incognito"
        const val PREF_DID_MD3_MIGRATION = "pref_did_md3_migration"

        const val PREF_RESIZE_KEYBOARD = "pref_resize_keyboard"
        const val PREF_KEYBOARD_HEIGHT_SCALE = "pref_keyboard_height_scale"

        const val PREF_SPACE_TRACKPAD = "pref_space_trackpad"
        const val PREF_DELETE_SWIPE = "pref_delete_swipe"
        // Language whose Varnam transliterations are suggested while typing on the English
        // keyboard; empty = feature off.
        const val PREF_COMPANION_LANGUAGE = "pref_companion_language"
        // Clipboard
        const val PREF_CLIPBOARD_ENABLED = "pref_clipboard_enabled"
        const val PREF_CLIPBOARD_RECENT_CHIP = "pref_clipboard_recent_chip"
        const val PREF_CLIPBOARD_EXPIRY_SECONDS = "pref_clipboard_expiry_seconds"
        // Emoji
        const val PREF_EMOJI_RECENT_KEYS = "emoji_recent_keys"
        const val PREF_EMOJI_CATEGORY_LAST_TYPED_ID = "emoji_category_last_typed_id"
        const val PREF_LAST_SHOWN_EMOJI_CATEGORY_ID = "last_shown_emoji_category_id"
        const val PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID = "last_shown_emoji_category_page_id"

        private const val UNDEFINED_PREFERENCE_VALUE_FLOAT = -1.0f
        private const val UNDEFINED_PREFERENCE_VALUE_INT = -1

        private const val ENABLED_SUBTYPE_KEY_SEPARATOR = ";"

        // Default keypress sound volume / vibration duration for unknown devices.
        // The negative value means system default.
        private val DEFAULT_KEYPRESS_SOUND_VOLUME = (-1.0f).toString()
        private val DEFAULT_KEYPRESS_VIBRATION_DURATION = (-1).toString()

        private val sInstance = Settings()

        @JvmStatic
        fun getInstance(): Settings = sInstance

        @JvmStatic
        fun init(context: Context) {
            sInstance.onCreate(context)
        }

        @JvmStatic
        fun readScreenMetrics(res: Resources): Int = res.getInteger(R.integer.config_screen_metrics)

        // Accessed from the settings interface, hence public
        @JvmStatic
        fun readKeypressSoundEnabled(prefs: SharedPreferences, res: Resources): Boolean =
            prefs.getBoolean(PREF_SOUND_ON, res.getBoolean(R.bool.config_default_sound_enabled))

        @JvmStatic
        fun readVibrationEnabled(prefs: SharedPreferences, res: Resources): Boolean {
            val hasVibrator = AudioAndHapticFeedbackManager.getInstance().hasVibrator()
            return hasVibrator && prefs.getBoolean(
                PREF_VIBRATE_ON, res.getBoolean(R.bool.config_default_vibration_enabled)
            )
        }

        @JvmStatic
        fun readAutoCorrectEnabled(prefs: SharedPreferences, res: Resources): Boolean =
            prefs.getBoolean(PREF_AUTO_CORRECTION, true)

        @JvmStatic
        fun readPlausibilityThreshold(res: Resources): Float =
            res.getString(R.string.plausibility_threshold).toFloat()

        @JvmStatic
        fun readBlockPotentiallyOffensive(prefs: SharedPreferences, res: Resources): Boolean =
            prefs.getBoolean(
                PREF_BLOCK_POTENTIALLY_OFFENSIVE,
                res.getBoolean(R.bool.config_block_potentially_offensive)
            )

        @JvmStatic
        fun readFromBuildConfigIfGestureInputEnabled(res: Resources): Boolean =
            res.getBoolean(R.bool.config_gesture_input_enabled_by_build_config)

        @JvmStatic
        fun readGestureInputEnabled(prefs: SharedPreferences, res: Resources): Boolean =
            readFromBuildConfigIfGestureInputEnabled(res) &&
                prefs.getBoolean(PREF_GESTURE_INPUT, true)

        @JvmStatic
        fun readFromBuildConfigIfToShowKeyPreviewPopupOption(res: Resources): Boolean =
            res.getBoolean(R.bool.config_enable_show_key_preview_popup_option)

        @JvmStatic
        fun readKeyPreviewPopupEnabled(prefs: SharedPreferences, res: Resources): Boolean {
            val defaultKeyPreviewPopup = res.getBoolean(R.bool.config_default_key_preview_popup)
            if (!readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
                return defaultKeyPreviewPopup
            }
            return prefs.getBoolean(PREF_POPUP_ON, defaultKeyPreviewPopup)
        }

        @JvmStatic
        fun readKeyPreviewPopupDismissDelay(prefs: SharedPreferences, res: Resources): Int =
            prefs.getString(
                PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                res.getInteger(R.integer.config_key_preview_linger_timeout).toString()
            )!!.toInt()

        @JvmStatic
        fun readShowsLanguageSwitchKey(prefs: SharedPreferences): Boolean {
            if (prefs.contains(PREF_SUPPRESS_LANGUAGE_SWITCH_KEY)) {
                val suppressLanguageSwitchKey =
                    prefs.getBoolean(PREF_SUPPRESS_LANGUAGE_SWITCH_KEY, false)
                prefs.edit {
                    remove(PREF_SUPPRESS_LANGUAGE_SWITCH_KEY)
                    putBoolean(PREF_SHOW_LANGUAGE_SWITCH_KEY, !suppressLanguageSwitchKey)
                }
            }
            return prefs.getBoolean(PREF_SHOW_LANGUAGE_SWITCH_KEY, true)
        }

        @JvmStatic
        fun readShowsEmojiSwitchKey(prefs: SharedPreferences): Boolean =
            prefs.getBoolean(PREF_SHOW_EMOJI_SWITCH_KEY, false)

        @JvmStatic
        fun readShowsNumberRow(prefs: SharedPreferences): Boolean =
            prefs.getBoolean(PREF_SHOW_NUMBER_ROW, true)

        @JvmStatic
        fun readPrefAdditionalSubtypes(prefs: SharedPreferences, res: Resources): String {
            val predefinedPrefSubtypes = AdditionalSubtypeUtils.createPrefSubtypes(
                res.getStringArray(R.array.predefined_subtypes)
            )
            return prefs.getString(PREF_CUSTOM_INPUT_STYLES, predefinedPrefSubtypes)!!
        }

        @JvmStatic
        fun writePrefAdditionalSubtypes(prefs: SharedPreferences, prefSubtypes: String) {
            prefs.edit { putString(PREF_CUSTOM_INPUT_STYLES, prefSubtypes) }
        }

        @JvmStatic
        fun hasEnabledSubtypes(prefs: SharedPreferences): Boolean =
            prefs.contains(PREF_ENABLED_SUBTYPES)

        @JvmStatic
        fun readEnabledSubtypeKeys(prefs: SharedPreferences): MutableSet<String> {
            val value = prefs.getString(PREF_ENABLED_SUBTYPES, "") ?: ""
            return if (value.isEmpty()) HashSet()
            else HashSet(value.split(ENABLED_SUBTYPE_KEY_SEPARATOR))
        }

        @JvmStatic
        fun writeEnabledSubtypeKeys(prefs: SharedPreferences, keys: Set<String>) {
            prefs.edit {
                putString(PREF_ENABLED_SUBTYPES, TextUtils.join(ENABLED_SUBTYPE_KEY_SEPARATOR, keys))
            }
        }

        // Reads that fall back to a default when the stored value is the sentinel "undefined".
        private inline fun SharedPreferences.floatOrDefault(key: String, default: () -> Float): Float =
            getFloat(key, UNDEFINED_PREFERENCE_VALUE_FLOAT).let {
                if (it != UNDEFINED_PREFERENCE_VALUE_FLOAT) it else default()
            }

        private inline fun SharedPreferences.intOrDefault(key: String, default: () -> Int): Int =
            getInt(key, UNDEFINED_PREFERENCE_VALUE_INT).let {
                if (it != UNDEFINED_PREFERENCE_VALUE_INT) it else default()
            }

        @JvmStatic
        fun readKeypressSoundVolume(prefs: SharedPreferences, res: Resources): Float =
            prefs.floatOrDefault(PREF_KEYPRESS_SOUND_VOLUME) { readDefaultKeypressSoundVolume(res) }

        @JvmStatic
        fun readDefaultKeypressSoundVolume(res: Resources): Float = ResourceUtils.getDeviceOverrideValue(
            res, R.array.keypress_volumes, DEFAULT_KEYPRESS_SOUND_VOLUME
        ).toFloat()

        @JvmStatic
        fun readKeyLongpressTimeout(prefs: SharedPreferences, res: Resources): Int =
            prefs.intOrDefault(PREF_KEY_LONGPRESS_TIMEOUT) { readDefaultKeyLongpressTimeout(res) }

        @JvmStatic
        fun readDefaultKeyLongpressTimeout(res: Resources): Int =
            res.getInteger(R.integer.config_default_longpress_key_timeout)

        @JvmStatic
        fun readKeypressVibrationDuration(prefs: SharedPreferences, res: Resources): Int =
            prefs.intOrDefault(PREF_VIBRATION_DURATION_SETTINGS) {
                readDefaultKeypressVibrationDuration(res)
            }

        @JvmStatic
        fun readDefaultKeypressVibrationDuration(res: Resources): Int =
            ResourceUtils.getDeviceOverrideValue(
                res, R.array.keypress_vibration_durations, DEFAULT_KEYPRESS_VIBRATION_DURATION
            ).toInt()

        @JvmStatic
        fun readKeyPreviewAnimationScale(
            prefs: SharedPreferences, prefKey: String, defaultValue: Float
        ): Float = prefs.floatOrDefault(prefKey) { defaultValue }

        @JvmStatic
        fun readKeyPreviewAnimationDuration(
            prefs: SharedPreferences, prefKey: String, defaultValue: Int
        ): Int = prefs.intOrDefault(prefKey) { defaultValue }

        @JvmStatic
        fun readKeyboardHeight(prefs: SharedPreferences, defaultValue: Float): Float =
            prefs.floatOrDefault(PREF_KEYBOARD_HEIGHT_SCALE) { defaultValue }

        @JvmStatic
        fun readSpaceTrackpadEnabled(prefs: SharedPreferences): Boolean =
            prefs.getBoolean(PREF_SPACE_TRACKPAD, true)

        @JvmStatic
        fun readDeleteSwipeEnabled(prefs: SharedPreferences): Boolean =
            prefs.getBoolean(PREF_DELETE_SWIPE, true)

        @JvmStatic
        fun readClipboardEnabled(prefs: SharedPreferences): Boolean =
            prefs.getBoolean(PREF_CLIPBOARD_ENABLED, true)

        @JvmStatic
        fun readCompanionLanguage(prefs: SharedPreferences): String =
            prefs.getString(PREF_COMPANION_LANGUAGE, "")!!

        @JvmStatic
        fun readClipboardExpiryMillis(prefs: SharedPreferences): Long =
            (prefs.getString(PREF_CLIPBOARD_EXPIRY_SECONDS, "3600")?.toLongOrNull() ?: 3600L) * 1000

        @JvmStatic
        fun readUseFullscreenMode(res: Resources): Boolean =
            res.getBoolean(R.bool.config_use_fullscreen_mode)

        @JvmStatic
        fun readShowSetupWizardIcon(prefs: SharedPreferences, context: Context): Boolean {
            if (!prefs.contains(PREF_SHOW_SETUP_WIZARD_ICON)) {
                val appInfo = context.applicationInfo
                val isApplicationInSystemImage =
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                // Default value
                return !isApplicationInSystemImage
            }
            return prefs.getBoolean(PREF_SHOW_SETUP_WIZARD_ICON, true)
        }

        @JvmStatic
        fun readHasHardwareKeyboard(conf: Configuration): Boolean {
            // The standard way of finding out whether we have a hardware keyboard. This code is
            // taken from InputMethodService#onEvaluateInputShown, which canonically determines
            // this. In a nutshell, we have a keyboard if the configuration says the type of
            // hardware keyboard is NOKEYS and if it's not hidden (e.g. folded inside the device).
            return conf.keyboard != Configuration.KEYBOARD_NOKEYS &&
                conf.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
        }

        @JvmStatic
        fun isInternal(prefs: SharedPreferences): Boolean =
            prefs.getBoolean(PREF_KEY_IS_INTERNAL, false)

        @JvmStatic
        fun writeEmojiRecentKeys(prefs: SharedPreferences, str: String) {
            prefs.edit { putString(PREF_EMOJI_RECENT_KEYS, str) }
        }

        @JvmStatic
        fun readEmojiRecentKeys(prefs: SharedPreferences): String =
            prefs.getString(PREF_EMOJI_RECENT_KEYS, "")!!

        /** Add an emoji to the recent-emojis list. */
        @JvmStatic
        fun addEmojiToRecentKeys(prefs: SharedPreferences, emoji: String?) {
            if (emoji.isNullOrEmpty()) {
                return
            }
            val toInsert: Any =
                if (emoji.codePointCount(0, emoji.length) == 1) emoji.codePointAt(0) else emoji

            val str = readEmojiRecentKeys(prefs)
            val keys = JsonUtils.jsonStrToList(str)
            // Most-recent first, matching the palette's own recents ordering (addKeyFirst). Move an
            // already-present emoji to the front rather than leaving it in place.
            keys.remove(toInsert)
            keys.add(0, toInsert)
            writeEmojiRecentKeys(prefs, JsonUtils.listToJsonStr(keys))
        }

        private fun emojiCategoryPageKey(categoryId: Int): String =
            PREF_EMOJI_CATEGORY_LAST_TYPED_ID + categoryId

        @JvmStatic
        fun writeLastTypedEmojiCategoryPageId(
            prefs: SharedPreferences, categoryId: Int, categoryPageId: Int
        ) {
            prefs.edit { putInt(emojiCategoryPageKey(categoryId), categoryPageId) }
        }

        @JvmStatic
        fun readLastTypedEmojiCategoryPageId(prefs: SharedPreferences, categoryId: Int): Int =
            prefs.getInt(emojiCategoryPageKey(categoryId), 0)

        @JvmStatic
        fun writeLastShownEmojiCategoryId(prefs: SharedPreferences, categoryId: Int) {
            prefs.edit { putInt(PREF_LAST_SHOWN_EMOJI_CATEGORY_ID, categoryId) }
        }

        @JvmStatic
        fun readLastShownEmojiCategoryId(prefs: SharedPreferences, defValue: Int): Int =
            prefs.getInt(PREF_LAST_SHOWN_EMOJI_CATEGORY_ID, defValue)

        @JvmStatic
        fun writeLastShownEmojiCategoryPageId(prefs: SharedPreferences, categoryId: Int) {
            prefs.edit { putInt(PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID, categoryId) }
        }

        @JvmStatic
        fun readLastShownEmojiCategoryPageId(prefs: SharedPreferences, defValue: Int): Int =
            prefs.getInt(PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID, defValue)
    }
}
