package org.smc.inputmethod.indic.varnam;

import android.util.Log;

import com.varnamproject.govarnam.Suggestion;

interface VarnamCallbackInterface {
    void onResult(String input, Suggestion[] sugs);
    void onResult(boolean settingLearn); // Init result
    void onResult();
    void onError(String er);
}

public class VarnamCallback implements VarnamCallbackInterface {
    @Override
    public void onResult(String input, Suggestion[] sugs) { }

    @Override
    public void onResult(boolean settingLearn) { }

    @Override
    public void onResult() { }
    @Override
    public void onError(String er) {
        Log.d("varnam-error", er);
    }
}