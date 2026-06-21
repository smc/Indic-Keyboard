/*
 * Copyright (C) 2013 The Android Open Source Project
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

package org.smc.inputmethod.indic.setup;

import android.content.Intent;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.inputmethod.compat.PreferenceManagerCompat;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.utils.KeyboardLanguages;
import com.android.inputmethod.latin.utils.KeyboardLanguages.Language;
import com.android.inputmethod.latin.utils.KeyboardLanguages.Layout;
import com.android.inputmethod.latin.utils.LeakGuardHandlerWrapper;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;
import com.android.inputmethod.latin.utils.TextDrawable;
import com.android.inputmethod.latin.utils.UncachedInputMethodManagerUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.smc.inputmethod.indic.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Modern Material 3 onboarding for the keyboard. A single adaptive screen guides the user through
 * the three Android-mandated steps - enable the IME, switch to it, then choose languages - by
 * updating its title, description and primary action per step. The underlying step state machine
 * (which system screen to open, and detecting when the user returns) is unchanged.
 */
public final class SetupWizardActivity extends AppCompatActivity implements View.OnClickListener {
    static final String TAG = SetupWizardActivity.class.getSimpleName();

    private InputMethodManager mImm;

    private View mSetupWizard;
    private TextView mWelcomeWord;
    private TextView mTitle;
    private TextView mDescription;
    private TextView mProgress;
    private MaterialButton mPrimaryAction;
    private String mApplicationName;

    private String[] mGreetings;
    private int mGreetingIndex;
    private static final int[] GREETING_GRADIENT =
            { 0xFF00BFA5, 0xFF2979FF, 0xFF7C4DFF };
    private static final long GREETING_HOLD_MS = 1500;
    private static final long GREETING_FADE_MS = 450;

    private static final String STATE_STEP = "step";
    private static final String STATE_FIRST_STEP = "first_step";
    private static final String EXTRA_FIRST_STEP = "first_step";
    private int mStepNumber;
    private int mFirstStep;
    private boolean mNeedsToAdjustStepNumberToSystemState;
    private static final int STEP_WELCOME = 0;
    private static final int STEP_1 = 1;
    private static final int STEP_2 = 2;
    private static final int STEP_LANGUAGES = 3;
    private static final int STEP_LAYOUTS = 4;
    private static final int STEP_DONE = 5;
    private static final int STEP_LAUNCHING_IME_SETTINGS = 6;
    private static final int STEP_BACK_FROM_IME_SETTINGS = 7;

    private OnBackPressedCallback mBackToPreviousStep;
    private View mLogo;
    private LinearLayout mSelectionList;
    private List<Language> mLanguages;
    private Set<String> mSelectedLocales;
    private Set<String> mEnabledKeys;

    private SettingsPoolingHandler mHandler;

    private static final class SettingsPoolingHandler
            extends LeakGuardHandlerWrapper<SetupWizardActivity> {
        private static final int MSG_POLLING_IME_SETTINGS = 0;
        private static final long IME_SETTINGS_POLLING_INTERVAL = 200;

        private final InputMethodManager mImmInHandler;

        public SettingsPoolingHandler(@Nonnull final SetupWizardActivity ownerInstance,
                final InputMethodManager imm) {
            super(ownerInstance);
            mImmInHandler = imm;
        }

        @Override
        public void handleMessage(final Message msg) {
            final SetupWizardActivity setupWizardActivity = getOwnerInstance();
            if (setupWizardActivity == null) {
                return;
            }
            switch (msg.what) {
            case MSG_POLLING_IME_SETTINGS:
                if (UncachedInputMethodManagerUtils.isThisImeEnabled(setupWizardActivity,
                        mImmInHandler)) {
                    setupWizardActivity.invokeSetupWizardOfThisIme();
                    return;
                }
                startPollingImeSettings();
                break;
            }
        }

        public void startPollingImeSettings() {
            sendMessageDelayed(obtainMessage(MSG_POLLING_IME_SETTINGS),
                    IME_SETTINGS_POLLING_INTERVAL);
        }

        public void cancelPollingImeSettings() {
            removeMessages(MSG_POLLING_IME_SETTINGS);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mImm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        mHandler = new SettingsPoolingHandler(this, mImm);
        mApplicationName = getResources().getString(getApplicationInfo().labelRes);

        setContentView(R.layout.setup_wizard);
        mSetupWizard = findViewById(R.id.setup_wizard);
        mLogo = findViewById(R.id.setup_logo);
        mWelcomeWord = findViewById(R.id.setup_welcome_word);
        mTitle = findViewById(R.id.setup_title);
        mDescription = findViewById(R.id.setup_description);
        mSelectionList = findViewById(R.id.setup_selection_list);
        mProgress = findViewById(R.id.setup_progress);
        mPrimaryAction = findViewById(R.id.setup_primary_action);
        mPrimaryAction.setOnClickListener(this);

        mBackToPreviousStep = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                mStepNumber = previousStep(mStepNumber);
                updateSetupStepView();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, mBackToPreviousStep);

        mGreetings = getResources().getStringArray(R.array.su_welcome_greetings);
        mWelcomeWord.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyGreetingGradient());

        ViewCompat.setOnApplyWindowInsetsListener(mSetupWizard,
                new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@Nonnull final View v,
                    @Nonnull final WindowInsetsCompat insets) {
                final Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            }
        });

        if (savedInstanceState == null) {
            final int carriedFirstStep = getIntent().getIntExtra(EXTRA_FIRST_STEP, 0);
            mFirstStep = carriedFirstStep != 0 ? carriedFirstStep : determineSetupStepNumber();
            mStepNumber = determineSetupStepNumberFromLauncher();
        } else {
            mStepNumber = savedInstanceState.getInt(STATE_STEP);
            mFirstStep = savedInstanceState.getInt(STATE_FIRST_STEP, STEP_1);
        }
    }

    private final Runnable mGreetingCycler = new Runnable() {
        @Override
        public void run() {
            mGreetingIndex = (mGreetingIndex + 1) % mGreetings.length;
            crossFadeToGreeting(mGreetingIndex);
            mWelcomeWord.postDelayed(this, GREETING_HOLD_MS + GREETING_FADE_MS * 2);
        }
    };

    private void startGreetingAnimation() {
        mGreetingIndex = 0;
        mWelcomeWord.animate().cancel();
        mWelcomeWord.setAlpha(1f);
        mWelcomeWord.setText(mGreetings[0]);
        applyGreetingGradient();
        mWelcomeWord.removeCallbacks(mGreetingCycler);
        if (mGreetings.length > 1) {
            mWelcomeWord.postDelayed(mGreetingCycler, GREETING_HOLD_MS + GREETING_FADE_MS);
        }
    }

    private void stopGreetingAnimation() {
        mWelcomeWord.removeCallbacks(mGreetingCycler);
        mWelcomeWord.animate().cancel();
    }

    private void crossFadeToGreeting(final int index) {
        mWelcomeWord.animate().alpha(0f).setDuration(GREETING_FADE_MS).withEndAction(
                new Runnable() {
            @Override
            public void run() {
                mWelcomeWord.setText(mGreetings[index]);
                applyGreetingGradient();
                mWelcomeWord.animate().alpha(1f).setDuration(GREETING_FADE_MS).start();
            }
        }).start();
    }

    private void applyGreetingGradient() {
        final CharSequence text = mWelcomeWord.getText();
        if (TextUtils.isEmpty(text) || mWelcomeWord.getWidth() == 0) {
            return;
        }
        final float textWidth = mWelcomeWord.getPaint().measureText(text.toString());
        if (textWidth <= 0f) {
            return;
        }
        final float available = mWelcomeWord.getWidth() - mWelcomeWord.getPaddingLeft()
                - mWelcomeWord.getPaddingRight();
        final float start = mWelcomeWord.getPaddingLeft() + Math.max(0f, (available - textWidth) / 2f);
        mWelcomeWord.getPaint().setShader(new LinearGradient(start, 0f, start + textWidth, 0f,
                GREETING_GRADIENT, null, Shader.TileMode.CLAMP));
        mWelcomeWord.invalidate();
    }

    @Override
    public void onClick(final View v) {
        if (v != mPrimaryAction) {
            return;
        }
        switch (mStepNumber) {
        case STEP_WELCOME:
            mStepNumber = determineSetupStepNumber();
            updateSetupStepView();
            break;
        case STEP_1:
            invokeLanguageAndInputSettings();
            mHandler.startPollingImeSettings();
            break;
        case STEP_2:
            invokeInputMethodPicker();
            break;
        case STEP_LANGUAGES:
            if (mSelectedLocales == null || mSelectedLocales.isEmpty()) {
                Toast.makeText(this, R.string.su_select_a_language, Toast.LENGTH_SHORT).show();
                break;
            }
            mStepNumber = STEP_LAYOUTS;
            updateSetupStepView();
            break;
        case STEP_LAYOUTS:
            commitSelectedLayouts();
            mStepNumber = STEP_DONE;
            updateSetupStepView();
            break;
        case STEP_DONE:
            finish();
            break;
        }
    }

    void invokeSetupWizardOfThisIme() {
        final Intent intent = new Intent();
        intent.setClass(this, SetupWizardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_FIRST_STEP, mFirstStep);
        startActivity(intent);
        mNeedsToAdjustStepNumberToSystemState = true;
    }

    private void invokeSettingsOfThisIme() {
        final Intent intent = new Intent();
        intent.setClass(this, SettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(SettingsActivity.EXTRA_ENTRY_KEY,
                SettingsActivity.EXTRA_ENTRY_VALUE_APP_ICON);
        startActivity(intent);
    }

    void invokeLanguageAndInputSettings() {
        final Intent intent = new Intent();
        intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        startActivity(intent);
        mNeedsToAdjustStepNumberToSystemState = true;
    }

    void invokeInputMethodPicker() {
        mImm.showInputMethodPicker();
        mNeedsToAdjustStepNumberToSystemState = true;
    }

    private int determineSetupStepNumberFromLauncher() {
        final int stepNumber = determineSetupStepNumber();
        if (stepNumber == STEP_1 || stepNumber == STEP_2) {
            return STEP_WELCOME;
        }
        return STEP_LAUNCHING_IME_SETTINGS;
    }

    private int determineSetupStepNumber() {
        mHandler.cancelPollingImeSettings();
        if (!UncachedInputMethodManagerUtils.isThisImeEnabled(this, mImm)) {
            return STEP_1;
        }
        if (!UncachedInputMethodManagerUtils.isThisImeCurrent(this, mImm)) {
            return STEP_2;
        }
        return STEP_LANGUAGES;
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, mStepNumber);
        outState.putInt(STATE_FIRST_STEP, mFirstStep);
    }

    @Override
    protected void onRestoreInstanceState(final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mStepNumber = savedInstanceState.getInt(STATE_STEP);
        mFirstStep = savedInstanceState.getInt(STATE_FIRST_STEP, mFirstStep);
    }

    private static boolean isInSetupSteps(final int stepNumber) {
        return stepNumber >= STEP_1 && stepNumber <= STEP_DONE;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mStepNumber == STEP_1 || mStepNumber == STEP_2) {
            mStepNumber = determineSetupStepNumber();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mStepNumber == STEP_LAUNCHING_IME_SETTINGS) {
            // Prevent flashing the wizard while launching the settings activity.
            mSetupWizard.setVisibility(View.INVISIBLE);
            invokeSettingsOfThisIme();
            mStepNumber = STEP_BACK_FROM_IME_SETTINGS;
            return;
        }
        if (mStepNumber == STEP_BACK_FROM_IME_SETTINGS) {
            finish();
            return;
        }
        updateSetupStepView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopGreetingAnimation();
    }

    private int previousStep(final int step) {
        switch (step) {
        case STEP_DONE:
            return STEP_LAYOUTS;
        case STEP_LAYOUTS:
            return STEP_LANGUAGES;
        case STEP_LANGUAGES:
        case STEP_2:
        case STEP_1:
            return STEP_WELCOME;
        default:
            return step;
        }
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mNeedsToAdjustStepNumberToSystemState) {
            mNeedsToAdjustStepNumberToSystemState = false;
            mStepNumber = determineSetupStepNumber();
            updateSetupStepView();
        }
    }

    private void updateSetupStepView() {
        mBackToPreviousStep.setEnabled(previousStep(mStepNumber) != mStepNumber);
        mSetupWizard.setVisibility(View.VISIBLE);
        final int titleRes;
        final int descRes;
        final int actionRes;
        final boolean showProgress;
        final boolean showList;
        switch (mStepNumber) {
        case STEP_1:
            titleRes = R.string.su_step1_title;
            descRes = R.string.su_step1_desc;
            actionRes = R.string.su_step1_action;
            showProgress = true;
            showList = false;
            break;
        case STEP_2:
            titleRes = R.string.su_step2_title;
            descRes = R.string.su_step2_desc;
            actionRes = R.string.su_step2_action;
            showProgress = true;
            showList = false;
            break;
        case STEP_LANGUAGES:
            titleRes = R.string.su_step3_title;
            descRes = R.string.su_step3_desc;
            actionRes = R.string.su_step3_action;
            showProgress = true;
            showList = true;
            break;
        case STEP_LAYOUTS:
            titleRes = R.string.su_step4_title;
            descRes = R.string.su_step4_desc;
            actionRes = R.string.su_step4_action;
            showProgress = true;
            showList = true;
            break;
        case STEP_DONE:
            titleRes = R.string.su_done_title;
            descRes = R.string.su_done_desc;
            actionRes = R.string.su_done_action;
            showProgress = false;
            showList = false;
            break;
        case STEP_WELCOME:
        default:
            mLogo.setVisibility(View.VISIBLE);
            mSelectionList.setVisibility(View.GONE);
            mTitle.setVisibility(View.GONE);
            mWelcomeWord.setVisibility(View.VISIBLE);
            mDescription.setText(R.string.su_welcome_subtitle);
            mPrimaryAction.setText(R.string.su_get_started);
            mProgress.setVisibility(View.GONE);
            startGreetingAnimation();
            return;
        }
        stopGreetingAnimation();
        mWelcomeWord.setVisibility(View.GONE);
        mLogo.setVisibility(showList ? View.GONE : View.VISIBLE);
        mTitle.setVisibility(View.VISIBLE);
        mTitle.setText(titleRes);
        mDescription.setText(descRes);
        mPrimaryAction.setText(actionRes);
        if (showList) {
            mSelectionList.setVisibility(View.VISIBLE);
            if (mStepNumber == STEP_LANGUAGES) {
                ensureSelectionStateLoaded();
                buildLanguageList();
            } else {
                buildLayoutList();
            }
        } else {
            mSelectionList.setVisibility(View.GONE);
        }
        if (showProgress) {
            final int total = Math.max(1, STEP_LAYOUTS - mFirstStep + 1);
            final int current = Math.min(total, Math.max(1, mStepNumber - mFirstStep + 1));
            mProgress.setVisibility(View.VISIBLE);
            mProgress.setText(getString(R.string.su_step_progress, current, total));
        } else {
            mProgress.setVisibility(View.GONE);
        }
    }

    private void ensureSelectionStateLoaded() {
        if (mLanguages != null) {
            return;
        }
        RichInputMethodManager.init(this);
        mLanguages = KeyboardLanguages.getLanguages(this);
        mEnabledKeys = new HashSet<>(
                org.smc.inputmethod.indic.settings.Settings.readEnabledSubtypeKeys(
                        PreferenceManagerCompat.getDeviceSharedPreferences(this)));
        mSelectedLocales = new LinkedHashSet<>();
        for (final Language language : mLanguages) {
            for (final Layout layout : language.mLayouts) {
                if (mEnabledKeys.contains(SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype))) {
                    mSelectedLocales.add(language.mLocale);
                    break;
                }
            }
        }
    }

    private void buildLanguageList() {
        mSelectionList.removeAllViews();
        final LayoutInflater inflater = getLayoutInflater();
        for (final Language language : sortedLanguages()) {
            final View row = inflater.inflate(R.layout.setup_selection_row, mSelectionList, false);
            ((ImageView) row.findViewById(R.id.selection_icon))
                    .setImageDrawable(createGlyphIcon(language));
            ((TextView) row.findViewById(R.id.selection_title)).setText(formatName(language));
            final MaterialSwitch toggle = row.findViewById(R.id.selection_switch);
            toggle.setChecked(mSelectedLocales.contains(language.mLocale));
            row.setOnClickListener(v -> {
                final boolean checked = !toggle.isChecked();
                toggle.setChecked(checked);
                if (checked) {
                    mSelectedLocales.add(language.mLocale);
                } else {
                    mSelectedLocales.remove(language.mLocale);
                }
            });
            mSelectionList.addView(row);
        }
    }

    private void buildLayoutList() {
        reconcileEnabledWithSelection();
        mSelectionList.removeAllViews();
        final LayoutInflater inflater = getLayoutInflater();
        for (final Language language : sortedLanguages()) {
            if (!mSelectedLocales.contains(language.mLocale)) {
                continue;
            }
            final TextView header = (TextView) inflater.inflate(
                    R.layout.setup_selection_header, mSelectionList, false);
            header.setText(formatName(language));
            mSelectionList.addView(header);
            for (final Layout layout : language.mLayouts) {
                final String key = SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype);
                final View row = inflater.inflate(
                        R.layout.setup_selection_row, mSelectionList, false);
                row.findViewById(R.id.selection_icon).setVisibility(View.GONE);
                ((TextView) row.findViewById(R.id.selection_title)).setText(layout.mName);
                final MaterialSwitch toggle = row.findViewById(R.id.selection_switch);
                toggle.setChecked(mEnabledKeys.contains(key));
                row.setOnClickListener(v -> {
                    final boolean checked = !toggle.isChecked();
                    toggle.setChecked(checked);
                    if (checked) {
                        mEnabledKeys.add(key);
                    } else {
                        mEnabledKeys.remove(key);
                    }
                });
                mSelectionList.addView(row);
            }
        }
    }

    private void reconcileEnabledWithSelection() {
        for (final Language language : mLanguages) {
            final boolean selected = mSelectedLocales.contains(language.mLocale);
            boolean hasEnabled = false;
            for (final Layout layout : language.mLayouts) {
                if (mEnabledKeys.contains(SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype))) {
                    hasEnabled = true;
                    break;
                }
            }
            if (selected && !hasEnabled && !language.mLayouts.isEmpty()) {
                mEnabledKeys.add(SubtypeLocaleUtils.getSubtypeKey(
                        language.mLayouts.get(0).mSubtype));
            } else if (!selected && hasEnabled) {
                for (final Layout layout : language.mLayouts) {
                    mEnabledKeys.remove(SubtypeLocaleUtils.getSubtypeKey(layout.mSubtype));
                }
            }
        }
    }

    private void commitSelectedLayouts() {
        reconcileEnabledWithSelection();
        RichInputMethodManager.init(this);
        RichInputMethodManager.getInstance().setEnabledSubtypeKeys(mEnabledKeys);
    }

    private List<Language> sortedLanguages() {
        final List<Language> sorted = new ArrayList<>(mLanguages);
        Collections.sort(sorted, new Comparator<Language>() {
            @Override
            public int compare(final Language a, final Language b) {
                final boolean aEn = a.mLocale.startsWith("en");
                final boolean bEn = b.mLocale.startsWith("en");
                if (aEn != bEn) {
                    return aEn ? -1 : 1;
                }
                return a.mEnglishName.compareToIgnoreCase(b.mEnglishName);
            }
        });
        return sorted;
    }

    private Drawable createGlyphIcon(final Language language) {
        final TypedValue value = new TypedValue();
        getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true);
        final int size = Math.round(40 * getResources().getDisplayMetrics().density);
        return new TextDrawable(language.mGlyph, value.data, 0 /* no background */, size);
    }

    private static CharSequence formatName(final Language language) {
        if (language.mEnglishName.equalsIgnoreCase(language.mAutonym)) {
            return language.mEnglishName;
        }
        return language.mEnglishName + " (" + language.mAutonym + ")";
    }
}
