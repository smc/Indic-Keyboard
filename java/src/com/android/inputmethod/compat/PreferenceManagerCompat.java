/*
 * Copyright (C) 2020 Raimondas Rimkus
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

package com.android.inputmethod.compat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PreferenceManagerCompat {
    private static final String TAG = "Indic Keyboard";

    private static final Set<String> sMigrated = Collections.synchronizedSet(new HashSet<>());

    private static void migrateOnce(final Context context, final Context deviceContext,
            final String name) {
        if (sMigrated.contains(name) || UserManagerCompatUtils.getUserLockState(context)
                == UserManagerCompatUtils.LOCK_STATE_LOCKED) {
            return;
        }
        if (deviceContext.moveSharedPreferencesFrom(context, name)) {
            sMigrated.add(name);
        } else {
            Log.w(TAG, "Failed to migrate shared preferences: " + name);
        }
    }

    public static Context getDeviceContext(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final Context deviceContext = context.createDeviceProtectedStorageContext();
            migrateOnce(context, deviceContext,
                    PreferenceManager.getDefaultSharedPreferencesName(context));
            return deviceContext;
        }

        return context;
    }

    public static SharedPreferences getDeviceSharedPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(getDeviceContext(context));
    }

    /**
     * Returns the named shared preferences backed by device protected storage, so they stay
     * accessible while the device is locked (direct boot, e.g. typing the password on the
     * keyguard). Migrates an existing credential protected file of the same name first.
     */
    public static SharedPreferences getDeviceSharedPreferences(Context context, String name,
            int mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final Context deviceContext = context.createDeviceProtectedStorageContext();
            migrateOnce(context, deviceContext, name);
            return deviceContext.getSharedPreferences(name, mode);
        }
        return context.getSharedPreferences(name, mode);
    }
}
