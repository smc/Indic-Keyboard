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

package org.smc.inputmethod.indic.languagepack;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.inputmethod.latin.BuildConfig;
import com.android.inputmethod.latin.utils.DictionaryInfoUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads per-language dictionary packs (one zip per language) from the data repo's releases,
 * verifies them, and installs their contents:
 *   - {@code main_<lang>.dict} is placed in the LatinIME cached-dictionary directory so the
 *     non-transliteration layouts pick it up ({@code filesDir/dicts/<lang>/main:<lang>});
 *   - {@code xlit_<lang>.dict} (romanized wordlist for gesture typing on transliteration
 *     layouts) stays in {@code filesDir/langpacks/<lang>/}, see {@link #xlitDictFile};
 *   - for varnam languages, {@code <lang>.vst} + {@code <lang>-*.vlf} are extracted into
 *     {@code filesDir/langpacks/<lang>/} and the {@code .vlf} are imported into the learnings DB.
 *
 * Lives in the same app as the IME, so files written here are visible to {@code LatinIME}.
 * Network access goes through the system {@link DownloadManager} (any network).
 */
public class LanguagePackDownloadManager {
    private static final String TAG = "LangPackDownload";

    // Stable "latest release" asset URL for the manifest, published by the dictionaries repo's
    // release workflow.
    public static final String INDEX_URL =
            "https://github.com/jishnu7/dictionaries/releases/latest/download/index.json";

    private static final String DL_SUBDIR = "langpack-dl";

    private static final String ASSETS_DIR = "langpacks";

    public static class Pack {
        public final String id;
        public final String lang;
        public final String name;
        public final String description;
        public final String url;
        public final long size;
        public final String sha256;
        public final int version;
        public final boolean hasVarnam;

        Pack(JSONObject o) {
            id = o.optString("id");
            lang = o.optString("lang");
            name = o.optString("name");
            description = o.optString("description");
            url = o.optString("url");
            size = o.optLong("size");
            sha256 = o.optString("sha256");
            version = o.optInt("version");
            hasVarnam = o.optBoolean("has_varnam", true);
        }
    }

    /** {@link Listener#onProgress} percent value meaning "extracting/importing, indeterminate". */
    public static final int INSTALLING = -1;

    public interface Listener {
        void onIndexLoaded(List<Pack> schemes);
        void onProgress(String lang, int percent);
        void onInstalled(String lang);
        void onError(String lang, String message);
    }

    private final Context appContext;
    private final DownloadManager downloadManager;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Active language downloads: downloadId -> scheme.
    private final Map<Long, Pack> active = new HashMap<>();
    private long indexDownloadId = -1;
    private Listener listener;
    private boolean polling;

    // One-shot "download packs for already-enabled languages" (upgrade migration) state.
    private List<String> bootstrapLangs;
    private int bootstrapVersion;

    public LanguagePackDownloadManager(Context context) {
        appContext = context.getApplicationContext();
        downloadManager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    // ---- Paths / install state (also used by the engine wrapper) ----

    public static File packDir(Context context, String lang) {
        return new File(new File(context.getFilesDir(), "langpacks"), lang);
    }

    public static File vstFile(Context context, String lang) {
        return new File(packDir(context, lang), lang + ".vst");
    }

    /** Romanized wordlist used as the gesture-decode dictionary on transliteration layouts. */
    public static File xlitDictFile(Context context, String lang) {
        return new File(packDir(context, lang), "xlit_" + lang + ".dict");
    }

    /** Where the downloaded {@code main_<lang>.dict} lands so the layouts' Suggest engine finds it. */
    public static File dictCacheFile(Context context, String lang) {
        final String id = DictionaryInfoUtils.getMainDictId(new Locale(lang));
        return new File(DictionaryInfoUtils.getCacheFileName(id, lang, context));
    }

    private static final String PREFS = "language_packs";
    private static final String KEY_VERSION = "version_";
    private static final String KEY_BOOTSTRAP_VERSION = "pack_bootstrap_version";

    /** A pack is installed once its version pref is recorded (set only after a full install). */
    public static boolean isInstalled(Context context, String lang) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(KEY_VERSION + lang);
    }

    /** Data version installed for {@code lang}: -1 if not installed, 0 if installed pre-versioning. */
    public static int installedVersion(Context context, String lang) {
        if (!isInstalled(context, lang)) {
            return -1;
        }
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_VERSION + lang, 0);
    }

    /** Marker the engine drops once it has imported the {@code .vlf} packs; cleared on update. */
    public static File importMarker(Context context, String lang) {
        return new File(packDir(context, lang), ".imported");
    }

    // ---- Manifest ----

    public void loadIndex() {
        if (BuildConfig.BUNDLED_LANGUAGE_PACKS) {
            io.execute(() -> {
                try {
                    final List<Pack> packs = bundledPacks();
                    maybeRunBootstrap(packs);
                    main.post(() -> {
                        if (listener != null) listener.onIndexLoaded(packs);
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "bundled index parse failed", e);
                    postError(null, e.getMessage());
                }
            });
            return;
        }
        final DownloadManager.Request req = new DownloadManager.Request(Uri.parse(INDEX_URL));
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
        req.setDestinationInExternalFilesDir(appContext, DL_SUBDIR, "index.json");
        indexDownloadId = downloadManager.enqueue(req);
        startPolling();
    }

    private List<Pack> bundledPacks() throws Exception {
        try (InputStream in = appContext.getAssets().open(ASSETS_DIR + "/index.json")) {
            final java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            copy(in, bos);
            return parseIndexJson(bos.toString("UTF-8"));
        }
    }

    // ---- Download ----

    /**
     * Upgrade migration: download packs for languages the user already has enabled but that
     * aren't installed yet (e.g. after an update that dropped the bundled dictionaries). Runs at
     * most once per app version — the version is recorded only after the index loads, so a failed
     * (offline) attempt retries on the next keyboard start. {@code en} has no pack and is skipped.
     */
    public void downloadMissingForEnabledLanguages(final int appVersionCode,
            final List<String> enabledLangCodes) {
        final android.content.SharedPreferences prefs =
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getInt(KEY_BOOTSTRAP_VERSION, -1) == appVersionCode) {
            return;
        }
        final List<String> missing = new ArrayList<>();
        for (final String code : enabledLangCodes) {
            if (!"en".equals(code) && !isInstalled(appContext, code) && !missing.contains(code)) {
                missing.add(code);
            }
        }
        if (missing.isEmpty()) {
            prefs.edit().putInt(KEY_BOOTSTRAP_VERSION, appVersionCode).apply();
            return;
        }
        bootstrapLangs = missing;
        bootstrapVersion = appVersionCode;
        loadIndex();  // downloads start in maybeRunBootstrap once the index arrives
    }

    private void maybeRunBootstrap(final List<Pack> schemes) {
        if (bootstrapLangs == null) {
            return;
        }
        final List<String> langs = bootstrapLangs;
        bootstrapLangs = null;
        main.post(() -> {
            for (final Pack s : schemes) {
                if (langs.contains(s.lang)) {
                    ensureDownloaded(s);
                }
            }
        });
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_BOOTSTRAP_VERSION, bootstrapVersion).apply();
    }

    /** Download {@code scheme} only if it isn't installed or a newer version is available. */
    public boolean ensureDownloaded(final Pack scheme) {
        final int installed = installedVersion(appContext, scheme.lang);
        if (installed >= 0 && scheme.version <= installed) {
            return false;
        }
        download(scheme);
        return true;
    }

    public void download(final Pack scheme) {
        if (BuildConfig.BUNDLED_LANGUAGE_PACKS) {
            main.post(() -> {
                if (listener != null) listener.onProgress(scheme.lang, INSTALLING);
            });
            io.execute(() -> {
                File zip = null;
                try {
                    zip = new File(appContext.getCacheDir(), scheme.lang + ".zip");
                    try (InputStream in = appContext.getAssets()
                                 .open(ASSETS_DIR + "/" + scheme.lang + ".zip");
                         OutputStream os = new FileOutputStream(zip)) {
                        copy(in, os);
                    }
                    installFromZip(zip, scheme);
                } catch (final Exception e) {
                    Log.e(TAG, "bundled install failed for " + scheme.lang, e);
                    postError(scheme.lang, e.getMessage());
                } finally {
                    if (zip != null) zip.delete();
                }
            });
            return;
        }
        final DownloadManager.Request req = new DownloadManager.Request(Uri.parse(scheme.url));
        req.setTitle(scheme.name);
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        req.setDestinationInExternalFilesDir(appContext, DL_SUBDIR, scheme.lang + ".zip");

        final long id = downloadManager.enqueue(req);
        active.put(id, scheme);
        startPolling();
    }

    private void startPolling() {
        if (polling) return;
        polling = true;
        main.post(pollRunnable);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            final List<Long> ids = new ArrayList<>(active.keySet());
            if (indexDownloadId != -1) ids.add(indexDownloadId);
            if (ids.isEmpty()) {
                polling = false;
                return;
            }
            final long[] idArray = new long[ids.size()];
            for (int i = 0; i < ids.size(); i++) idArray[i] = ids.get(i);

            final DownloadManager.Query q = new DownloadManager.Query().setFilterById(idArray);
            try (Cursor c = downloadManager.query(q)) {
                while (c != null && c.moveToNext()) {
                    handleRow(c);
                }
            }
            if (!active.isEmpty() || indexDownloadId != -1) {
                main.postDelayed(this, 600);
            } else {
                polling = false;
            }
        }
    };

    private void handleRow(final Cursor c) {
        final long id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
        final int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));

        if (id == indexDownloadId) {
            handleIndexRow(status);
            return;
        }

        final Pack scheme = active.get(id);
        if (scheme == null) return;

        if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
            final long done = c.getLong(
                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            final long total = c.getLong(
                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            final int pct = total > 0 ? (int) (done * 100 / total) : 0;
            if (listener != null) listener.onProgress(scheme.lang, pct);
        } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
            active.remove(id);
            // Read the file via its download id before remove() — remove() deletes it.
            finishDownload(id, scheme, downloadManager.getUriForDownloadedFile(id));
        } else if (status == DownloadManager.STATUS_FAILED) {
            active.remove(id);
            downloadManager.remove(id);
            postError(scheme.lang, "Download failed");
        }
    }

    private void handleIndexRow(final int status) {
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            final long id = indexDownloadId;
            indexDownloadId = -1;
            // Read by download id (DownloadManager renames on filename collision); remove() after.
            final Uri uri = downloadManager.getUriForDownloadedFile(id);
            io.execute(() -> {
                try {
                    final List<Pack> schemes = fetchAndCacheIndex(uri);
                    maybeRunBootstrap(schemes);
                    main.post(() -> {
                        if (listener != null) listener.onIndexLoaded(schemes);
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "index parse failed", e);
                    postError(null, e.getMessage());
                } finally {
                    downloadManager.remove(id);
                }
            });
        } else if (status == DownloadManager.STATUS_FAILED) {
            final long id = indexDownloadId;
            indexDownloadId = -1;
            downloadManager.remove(id);
            postError(null, "Could not load language list");
        }
    }

    /** Read the downloaded index, persist it to the cache file, and parse it. */
    private List<Pack> fetchAndCacheIndex(final Uri uri) throws Exception {
        final java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (InputStream in = appContext.getContentResolver().openInputStream(uri)) {
            copy(in, bos);
        }
        final byte[] bytes = bos.toByteArray();
        try (OutputStream os = new FileOutputStream(indexCacheFile())) {
            os.write(bytes);
        } catch (final IOException e) {
            Log.e(TAG, "could not cache index", e);
        }
        return parseIndexJson(new String(bytes, "UTF-8"));
    }

    /** Schemes from the last successfully fetched index, or empty if none cached yet. */
    public List<Pack> cachedPacks() {
        if (BuildConfig.BUNDLED_LANGUAGE_PACKS) {
            try {
                return bundledPacks();
            } catch (final Exception e) {
                Log.e(TAG, "could not read bundled index", e);
                return new ArrayList<>();
            }
        }
        final File cache = indexCacheFile();
        if (!cache.exists()) {
            return new ArrayList<>();
        }
        try (InputStream in = new FileInputStream(cache)) {
            final java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            copy(in, bos);
            return parseIndexJson(bos.toString("UTF-8"));
        } catch (final Exception e) {
            Log.e(TAG, "could not read cached index", e);
            return new ArrayList<>();
        }
    }

    private File indexCacheFile() {
        final File dir = new File(appContext.getFilesDir(), "langpacks");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "index.json");
    }

    private static List<Pack> parseIndexJson(final String json) throws Exception {
        final JSONArray arr = new JSONObject(json).getJSONArray("schemes");
        final List<Pack> schemes = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            schemes.add(new Pack(arr.getJSONObject(i)));
        }
        return schemes;
    }

    private void finishDownload(final long id, final Pack scheme, final Uri localUri) {
        io.execute(() -> {
            File zip = null;
            try {
                zip = copyToCache(localUri, scheme.lang);
                installFromZip(zip, scheme);
            } catch (final Exception e) {
                Log.e(TAG, "install failed for " + scheme.lang, e);
                postError(scheme.lang, e.getMessage());
            } finally {
                downloadManager.remove(id);
                if (zip != null) zip.delete();
            }
        });
    }

    private void installFromZip(final File zip, final Pack scheme) throws Exception {
        final File dir = packDir(appContext, scheme.lang);
        final File staging = new File(dir.getParentFile(), scheme.lang + ".staging");
        try {
            verifySha256(zip, scheme.sha256);
            deleteRecursive(staging);
            extractTo(zip, staging);
            commitStaging(staging, dir);
            installDict(scheme.lang, dir);
            main.post(() -> {
                if (listener != null) listener.onProgress(scheme.lang, INSTALLING);
            });
            if (scheme.hasVarnam) {
                importMarker(appContext, scheme.lang).delete();
                importPacks(scheme.lang);
            }
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_VERSION + scheme.lang, scheme.version).apply();
            main.post(() -> {
                if (listener != null) listener.onInstalled(scheme.lang);
            });
        } finally {
            deleteRecursive(staging);
        }
    }

    /** Move extracted pack files from staging into dir, leaving .learnings intact. */
    private static void commitStaging(final File staging, final File dir) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        final File[] files = staging.listFiles();
        if (files == null || files.length == 0) {
            throw new IOException("nothing extracted");
        }
        for (final File f : files) {
            moveInto(f, dir);
        }
    }

    /** Copy {@code main_<lang>.dict} into the LatinIME cache (temp + rename for atomicity). */
    private void installDict(final String lang, final File dir) throws IOException {
        final File dict = new File(dir, "main_" + lang + ".dict");
        if (!dict.exists()) {
            return;
        }
        final File dest = dictCacheFile(appContext, lang);
        final File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        final File tmp = new File(dest.getPath() + ".tmp");
        try (InputStream in = new FileInputStream(dict);
             OutputStream os = new FileOutputStream(tmp)) {
            copy(in, os);
        }
        dest.delete();
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            throw new IOException("could not place " + dest.getName());
        }
    }

    private static void moveInto(final File f, final File dir) throws IOException {
        final File dest = new File(dir, f.getName());
        dest.delete();
        if (!f.renameTo(dest)) {
            throw new IOException("could not place " + f.getName());
        }
    }

    /**
     * Imports the downloaded {@code .vlf} packs into the learnings DB and marks it done, so the
     * first keyboard activation doesn't stall on the import. If the native engine isn't usable
     * here the marker is left unset and the IME-side wrapper imports lazily as a fallback.
     */
    private void importPacks(final String lang) {
        final File dir = packDir(appContext, lang);
        final File[] vlfs = dir.listFiles((d, name) -> name.endsWith(".vlf"));
        if (vlfs == null || vlfs.length == 0) {
            return;
        }
        final File learnings = new File(dir, lang + ".learnings");
        com.varnamproject.govarnam.Varnam engine = null;
        boolean ok = true;
        try {
            engine = new com.varnamproject.govarnam.Varnam(
                    vstFile(appContext, lang).getAbsolutePath(), learnings.getAbsolutePath());
            for (final File vlf : vlfs) {
                try {
                    engine.importFromFile(vlf.getAbsolutePath());
                } catch (final Exception e) {
                    Log.e(TAG, "import failed: " + vlf.getName(), e);
                    ok = false;
                }
            }
        } catch (final Throwable t) {
            Log.e(TAG, "pack import skipped (engine unavailable)", t);
            ok = false;
        } finally {
            if (engine != null) {
                engine.close();
            }
        }
        if (ok) {
            try {
                importMarker(appContext, lang).createNewFile();
            } catch (final IOException e) {
                Log.e(TAG, "could not mark imports done", e);
            }
        }
    }

    // ---- Delete ----

    public void delete(final String lang) {
        io.execute(() -> {
            deleteRecursive(packDir(appContext, lang));
            dictCacheFile(appContext, lang).delete();
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_VERSION + lang).apply();
        });
    }

    // ---- Helpers ----

    private File copyToCache(final Uri uri, final String lang) throws IOException {
        final File out = new File(appContext.getCacheDir(), lang + ".zip");
        try (InputStream in = appContext.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            copy(in, os);
        }
        return out;
    }

    private static void verifySha256(final File file, final String expected) throws Exception {
        if (expected == null || expected.isEmpty()) return;
        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            final byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        final StringBuilder sb = new StringBuilder();
        for (final byte b : md.digest()) sb.append(String.format(Locale.US, "%02x", b));
        if (!sb.toString().equalsIgnoreCase(expected)) {
            throw new IOException("checksum mismatch");
        }
    }

    private static void extractTo(final File zip, final File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("cannot create " + destDir);
        }
        final String destPath = destDir.getCanonicalPath();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                // Flatten and guard against zip-slip: keep the bare file name only.
                final String name = new File(entry.getName()).getName();
                final File out = new File(destDir, name);
                if (!out.getCanonicalPath().startsWith(destPath)) {
                    throw new IOException("bad zip entry: " + entry.getName());
                }
                try (OutputStream os = new FileOutputStream(out)) {
                    copy(zis, os);
                }
            }
        }
    }

    private static void copy(final InputStream in, final OutputStream out) throws IOException {
        final byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }

    private static void deleteRecursive(final File f) {
        if (f == null || !f.exists()) return;
        final File[] children = f.listFiles();
        if (children != null) {
            for (final File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    private void postError(final String lang, final String message) {
        main.post(() -> {
            if (listener != null) listener.onError(lang, message);
        });
    }
}
