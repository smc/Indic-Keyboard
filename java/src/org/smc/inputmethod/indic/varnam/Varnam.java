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

package org.smc.inputmethod.indic.varnam;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.varnamproject.govarnam.Suggestion;
import com.varnamproject.govarnam.TransliterationResult;
import org.smc.inputmethod.indic.languagepack.LanguagePackDownloadManager;
import com.varnamproject.govarnam.VarnamException;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-process Varnam transliteration, backed by the embedded govarnam engine
 * ({@link com.varnamproject.govarnam.Varnam}, native {@code libgovarnam_jni.so}). Reads the
 * {@code .vst} scheme table and {@code .vlf} learnings packs that {@link LanguagePackDownloadManager}
 * downloaded into {@code filesDir/varnam/<scheme>/}.
 *
 * Keeps the same public surface the old IPC client exposed, so {@code InputLogic} is unchanged:
 * an async constructor that calls back on init, plus {@link #transliterate}, {@link #learn},
 * {@link #cancel}, the suggestion-limit setters and {@link #close}.
 *
 * The engine handle is not thread-safe, so all engine calls are serialized on a single
 * background thread; {@link #cancel} is the one exception — it runs inline so it can abort an
 * in-flight transliterate of the same id.
 */
public class Varnam {
    private static final String TAG = "varnam";

    public static final String ERROR_VST_MISSING = "vst-missing";
    public static final String ERROR_ENGINE_MISSING = "engine-missing";

    private final String schemeID;
    private final Context appContext;
    private final ExecutorService engineThread = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private com.varnamproject.govarnam.Varnam engine;

    public Varnam(final String schemeID, final Context context, final VarnamCallback cb) {
        this.schemeID = schemeID;
        this.appContext = context.getApplicationContext();
        engineThread.execute(() -> init(cb));
    }

    private void init(final VarnamCallback cb) {
        final File vst = LanguagePackDownloadManager.vstFile(appContext, schemeID);
        if (!vst.exists()) {
            post(() -> cb.onError(ERROR_VST_MISSING));
            return;
        }
        final File dir = LanguagePackDownloadManager.packDir(appContext, schemeID);
        final File learnings = new File(dir, schemeID + ".learnings");
        try {
            engine = new com.varnamproject.govarnam.Varnam(vst.getAbsolutePath(),
                    learnings.getAbsolutePath());
            importLearnings(dir);
            post(() -> cb.onResult(true /* settingLearn */));
        } catch (final UnsatisfiedLinkError e) {
            Log.e(TAG, "govarnam native library missing", e);
            post(() -> cb.onError(ERROR_ENGINE_MISSING));
        } catch (final VarnamException e) {
            Log.e(TAG, "varnam init failed", e);
            post(() -> cb.onError(e.getMessage()));
        }
    }

    /** Imports the downloaded {@code .vlf} packs into the learnings DB once per download. */
    private void importLearnings(final File dir) {
        final File marker = LanguagePackDownloadManager.importMarker(appContext, schemeID);
        if (marker.exists()) {
            return;
        }
        final File[] vlfs = dir.listFiles((d, name) -> name.endsWith(".vlf"));
        if (vlfs == null) {
            return;
        }
        boolean ok = true;
        for (final File vlf : vlfs) {
            try {
                engine.importFromFile(vlf.getAbsolutePath());
            } catch (final VarnamException e) {
                Log.e(TAG, "import failed: " + vlf.getName(), e);
                ok = false;
            }
        }
        if (ok) {
            try {
                marker.createNewFile();
            } catch (final Exception e) {
                Log.e(TAG, "could not mark imports done", e);
            }
        }
    }

    public void setDictionarySuggestionsLimit(final int limit) {
        engineThread.execute(() -> {
            if (engine != null) engine.setDictionarySuggestionsLimit(limit);
        });
    }

    public void setTokenizerSuggestionsLimit(final int limit) {
        engineThread.execute(() -> {
            if (engine != null) engine.setTokenizerSuggestionsLimit(limit);
        });
    }

    public void transliterate(final int id, final String input, final VarnamCallback cb) {
        engineThread.execute(() -> {
            if (engine == null) {
                return;
            }
            try {
                final Suggestion[] sugs = engine.transliterate(id, input);
                post(() -> cb.onResult(input, sugs));
            } catch (final VarnamException e) {
                post(() -> cb.onError(e.getMessage()));
            }
        });
    }

    /** Receives the categorized result of {@link #transliterateAdvanced}. */
    public interface TransliterationResultCallback {
        void onResult(String input, TransliterationResult result);
    }

    /**
     * Like {@link #transliterate} but keeps the engine's categories (exact matches, dictionary
     * words, tokenizer guesses) separate, so callers can judge confidence.
     */
    public void transliterateAdvanced(final int id, final String input,
            final TransliterationResultCallback cb) {
        engineThread.execute(() -> {
            if (engine == null) {
                return;
            }
            try {
                final TransliterationResult result = engine.transliterateAdvanced(id, input);
                post(() -> cb.onResult(input, result));
            } catch (final VarnamException e) {
                Log.w(TAG, "transliterateAdvanced failed", e);
            }
        });
    }

    public void cancel(final int id) {
        if (engine != null) {
            engine.cancel(id);
        }
    }

    public void learn(final String input) {
        engineThread.execute(() -> {
            if (engine == null) return;
            try {
                engine.learn(input);
            } catch (final VarnamException e) {
                Log.e(TAG, "learn failed", e);
            }
        });
    }

    public void close(final Context context) {
        engineThread.execute(() -> {
            if (engine != null) {
                engine.close();
                engine = null;
            }
        });
        engineThread.shutdown();
    }

    private void post(final Runnable r) {
        main.post(r);
    }
}
