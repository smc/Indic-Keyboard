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

package org.smc.inputmethod.indic.clipboard;

import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;

public final class ClipboardHistoryEntry {
    // Fixed-length mask so a sensitive entry's length is not leaked.
    private static final String SENSITIVE_MASK = "••••••••";

    public final long mTimestamp;
    @Nullable public final CharSequence mText;
    @Nullable public final String mImageFileName;
    @Nullable public final String mMimeType;
    public final boolean mSensitive;

    public static ClipboardHistoryEntry ofText(final long timestamp, final CharSequence text,
            final boolean sensitive) {
        return new ClipboardHistoryEntry(timestamp, text, null, null, sensitive);
    }

    public static ClipboardHistoryEntry ofImage(final long timestamp, final String imageFileName,
            final String mimeType) {
        return new ClipboardHistoryEntry(timestamp, null, imageFileName, mimeType, false);
    }

    private ClipboardHistoryEntry(final long timestamp, @Nullable final CharSequence text,
            @Nullable final String imageFileName, @Nullable final String mimeType,
            final boolean sensitive) {
        mTimestamp = timestamp;
        mText = text;
        mImageFileName = imageFileName;
        mMimeType = mimeType;
        mSensitive = sensitive;
    }

    public boolean isImage() {
        return mImageFileName != null;
    }

    /** What to render in the chip and history list; pasting still uses {@link #mText}. */
    public CharSequence getDisplayText() {
        return mSensitive ? SENSITIVE_MASK : mText;
    }

    public JSONObject toJson() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("timestamp", mTimestamp);
        if (mText != null) {
            json.put("text", mText.toString());
        }
        if (mSensitive) {
            json.put("sensitive", true);
        }
        if (mImageFileName != null) {
            json.put("imageFileName", mImageFileName);
            json.put("mimeType", mMimeType);
        }
        return json;
    }

    @Nullable
    public static ClipboardHistoryEntry fromJson(final JSONObject json) {
        final long timestamp = json.optLong("timestamp");
        final String text = json.optString("text", null);
        if (text != null) {
            return ofText(timestamp, text, json.optBoolean("sensitive"));
        }
        final String imageFileName = json.optString("imageFileName", null);
        if (imageFileName != null) {
            return ofImage(timestamp, imageFileName, json.optString("mimeType", "image/*"));
        }
        return null;
    }
}
