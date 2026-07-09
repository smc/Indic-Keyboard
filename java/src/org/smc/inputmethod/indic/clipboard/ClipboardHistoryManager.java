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

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.android.inputmethod.compat.PreferenceManagerCompat;
import com.android.inputmethod.latin.utils.ExecutorUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smc.inputmethod.indic.settings.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public final class ClipboardHistoryManager implements ClipboardManager.OnPrimaryClipChangedListener {
    private static final String TAG = ClipboardHistoryManager.class.getSimpleName();

    private static final int MAX_ENTRIES = 25;
    private static final long MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final String HISTORY_DIR = "clipboard";
    private static final String IMAGES_DIR = "images";
    private static final String HISTORY_FILE = "history.json";
    private static final String FILE_PROVIDER_AUTHORITY_SUFFIX = ".clipboard.fileprovider";

    public interface OnHistoryChangedListener {
        void onClipboardHistoryChanged();
    }

    private static ClipboardHistoryManager sInstance;

    public static synchronized ClipboardHistoryManager init(final Context context) {
        if (sInstance == null) {
            sInstance = new ClipboardHistoryManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private final Context mContext;
    private final ClipboardManager mClipboardManager;
    private final ArrayList<ClipboardHistoryEntry> mEntries = new ArrayList<>();
    private boolean mLoaded;
    @Nullable private OnHistoryChangedListener mOnHistoryChangedListener;

    private ClipboardHistoryManager(final Context context) {
        mContext = context;
        mClipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public void startListening() {
        mClipboardManager.addPrimaryClipChangedListener(this);
    }

    public void stopListening() {
        mClipboardManager.removePrimaryClipChangedListener(this);
    }

    public void setOnHistoryChangedListener(@Nullable final OnHistoryChangedListener listener) {
        mOnHistoryChangedListener = listener;
    }

    @Override
    public void onPrimaryClipChanged() {
        final SharedPreferences prefs =
                PreferenceManagerCompat.getDeviceSharedPreferences(mContext);
        if (!Settings.readClipboardEnabled(prefs)) {
            return;
        }
        final ClipDescription description = mClipboardManager.getPrimaryClipDescription();
        if (description == null) {
            return;
        }
        if (description.hasMimeType("image/*")) {
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD)
                    .execute(this::captureImageClip);
        } else if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                || description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
            captureTextClip(isSensitive(description));
        }
    }

    private static boolean isSensitive(final ClipDescription description) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        // The extra key predates its API 33 constant; password managers set it on older
        // versions too.
        return description.getExtras() != null
                && description.getExtras().getBoolean("android.content.extra.IS_SENSITIVE");
    }

    private void captureTextClip(final boolean sensitive) {
        final ClipData clip = getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        final CharSequence text = clip.getItemAt(0).coerceToText(mContext);
        if (text == null || text.length() == 0) {
            return;
        }
        synchronized (mEntries) {
            loadLocked();
            if (!mEntries.isEmpty() && !mEntries.get(0).isImage()
                    && text.toString().contentEquals(mEntries.get(0).mText)) {
                mEntries.remove(0);
            }
            mEntries.add(0, ClipboardHistoryEntry.ofText(
                    System.currentTimeMillis(), text, sensitive));
            pruneLocked();
        }
        onHistoryMutated();
    }

    private void captureImageClip() {
        final ClipData clip = getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        final Uri uri = clip.getItemAt(0).getUri();
        if (uri == null) {
            return;
        }
        final String mimeType = mContext.getContentResolver().getType(uri);
        if (mimeType == null || !mimeType.startsWith("image/")) {
            return;
        }
        final long timestamp = System.currentTimeMillis();
        final String fileName = timestamp + extensionFor(mimeType);
        final File imageFile = new File(getImagesDir(), fileName);
        // The cross-app URI grant is transient, so the bytes must be copied out now.
        try (InputStream in = mContext.getContentResolver().openInputStream(uri);
                FileOutputStream out = new FileOutputStream(imageFile)) {
            if (in == null) {
                return;
            }
            final byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    out.close();
                    imageFile.delete();
                    return;
                }
                out.write(buffer, 0, read);
            }
        } catch (final IOException | SecurityException e) {
            Log.w(TAG, "Could not copy clipboard image", e);
            imageFile.delete();
            return;
        }
        synchronized (mEntries) {
            loadLocked();
            mEntries.add(0, ClipboardHistoryEntry.ofImage(timestamp, fileName, mimeType));
            pruneLocked();
        }
        onHistoryMutated();
    }

    @Nullable
    private ClipData getPrimaryClip() {
        try {
            return mClipboardManager.getPrimaryClip();
        } catch (final SecurityException e) {
            Log.w(TAG, "Could not read clipboard", e);
            return null;
        }
    }

    public List<ClipboardHistoryEntry> getHistory() {
        final boolean changed;
        final ArrayList<ClipboardHistoryEntry> copy;
        synchronized (mEntries) {
            loadLocked();
            changed = pruneLocked();
            copy = new ArrayList<>(mEntries);
        }
        if (changed) {
            persist();
        }
        return copy;
    }

    @Nullable
    public ClipboardHistoryEntry getRecentEntry(final long maxAgeMillis) {
        final List<ClipboardHistoryEntry> history = getHistory();
        if (history.isEmpty()) {
            return null;
        }
        final ClipboardHistoryEntry newest = history.get(0);
        return System.currentTimeMillis() - newest.mTimestamp <= maxAgeMillis ? newest : null;
    }

    public void deleteEntry(final ClipboardHistoryEntry entry) {
        synchronized (mEntries) {
            loadLocked();
            for (int i = 0; i < mEntries.size(); i++) {
                if (mEntries.get(i).mTimestamp == entry.mTimestamp) {
                    deleteImageFile(mEntries.remove(i));
                    break;
                }
            }
        }
        onHistoryMutated();
    }

    public void clearHistory() {
        synchronized (mEntries) {
            loadLocked();
            for (final ClipboardHistoryEntry entry : mEntries) {
                deleteImageFile(entry);
            }
            mEntries.clear();
        }
        onHistoryMutated();
    }

    public Uri contentUriFor(final ClipboardHistoryEntry entry) {
        return FileProvider.getUriForFile(mContext,
                mContext.getPackageName() + FILE_PROVIDER_AUTHORITY_SUFFIX,
                new File(getImagesDir(), entry.mImageFileName));
    }

    public File imageFileFor(final ClipboardHistoryEntry entry) {
        return new File(getImagesDir(), entry.mImageFileName);
    }

    private void onHistoryMutated() {
        persist();
        final OnHistoryChangedListener listener = mOnHistoryChangedListener;
        if (listener != null) {
            listener.onClipboardHistoryChanged();
        }
    }

    private void loadLocked() {
        if (mLoaded) {
            return;
        }
        mLoaded = true;
        final File historyFile = getHistoryFile();
        if (!historyFile.exists()) {
            return;
        }
        try {
            final String content = new String(Files.readAllBytes(historyFile.toPath()),
                    StandardCharsets.UTF_8);
            final JSONArray array = new JSONArray(content);
            for (int i = 0; i < array.length(); i++) {
                final ClipboardHistoryEntry entry =
                        ClipboardHistoryEntry.fromJson(array.getJSONObject(i));
                if (entry != null) {
                    mEntries.add(entry);
                }
            }
        } catch (final IOException | JSONException e) {
            Log.w(TAG, "Could not load clipboard history", e);
        }
    }

    private boolean pruneLocked() {
        final SharedPreferences prefs =
                PreferenceManagerCompat.getDeviceSharedPreferences(mContext);
        final long expiryMillis = Settings.readClipboardExpiryMillis(prefs);
        final long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = mEntries.size() - 1; i >= 0; i--) {
            final ClipboardHistoryEntry entry = mEntries.get(i);
            final boolean expired = expiryMillis > 0 && now - entry.mTimestamp > expiryMillis;
            if (expired || i >= MAX_ENTRIES) {
                deleteImageFile(mEntries.remove(i));
                changed = true;
            }
        }
        return changed;
    }

    private void deleteImageFile(final ClipboardHistoryEntry entry) {
        if (entry.isImage()) {
            imageFileFor(entry).delete();
        }
    }

    private void persist() {
        final JSONArray array = new JSONArray();
        synchronized (mEntries) {
            for (final ClipboardHistoryEntry entry : mEntries) {
                try {
                    array.put(entry.toJson());
                } catch (final JSONException e) {
                    Log.w(TAG, "Could not serialize clipboard entry", e);
                }
            }
        }
        final String content = array.toString();
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(() -> {
            try (FileOutputStream out = new FileOutputStream(getHistoryFile())) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            } catch (final IOException e) {
                Log.w(TAG, "Could not save clipboard history", e);
            }
        });
    }

    private File getHistoryFile() {
        return new File(getHistoryDir(), HISTORY_FILE);
    }

    private File getImagesDir() {
        final File dir = new File(getHistoryDir(), IMAGES_DIR);
        dir.mkdirs();
        return dir;
    }

    private File getHistoryDir() {
        final File dir = new File(mContext.getFilesDir(), HISTORY_DIR);
        dir.mkdirs();
        return dir;
    }

    private static String extensionFor(final String mimeType) {
        switch (mimeType) {
            case "image/png": return ".png";
            case "image/jpeg": return ".jpg";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            default: return ".img";
        }
    }
}
