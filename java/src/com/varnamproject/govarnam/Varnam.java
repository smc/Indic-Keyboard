/*
 * Varnam JNI Interface
 * Copyright 2021, Subin Siby
 * https://gitlab.com/indicproject/varnam/libvarnam-java
 * Licensed under LGPL-3.0
 */

package com.varnamproject.govarnam;

import java.util.Arrays;
import java.util.List;

public final class Varnam {
    int VARNAM_SUCCESS = 0;
    int VARNAM_MISUSE = 1;
    int VARNAM_ERROR = 2;

    static {
        System.loadLibrary("govarnam_jni");
    }

    private static native SchemeDetails[] varnam_get_all_scheme_details();

    private native int varnam_init(String vstFile, String learningsFile);
    private native String varnam_get_last_error(int handle);
    private native int varnam_close(int handle);

    private native Suggestion[] varnam_transliterate(int handle, int id, String word);
    private native TransliterationResult varnam_transliterate_advanced(int handle, int id, String word);

    private native int varnam_learn(int handle, String word);
    private native int varnam_unlearn(int handle, String word);

    private native int varnam_cancel(int id);

    private native void varnam_set_dictionary_suggestions_limit(int handle, int limit);

    private native void varnam_set_tokenizer_suggestions_limit(int handle, int limit);

    private native LearnStatus varnam_learn_from_file(int handle, String path);
    private native int varnam_import(int handle, String path);
    private native int varnam_export(int handle, String path, int wordsPerFile);

    private native static void varnam_set_vst_lookup_dir(String path);
    private native Suggestion[] varnam_get_recently_learned_words(int handle, int id, int offset, int limit);

    private int handle;

    public Varnam(String vstFile, String learningsFile) throws VarnamException {
        // handle value will be set by JNI C
        int status = varnam_init(vstFile, learningsFile);
        if (status != VARNAM_SUCCESS) {
            throw new VarnamException("Error initializing varnam." + getLastError());
        }
    }

    public String getLastError() {
        return varnam_get_last_error(this.handle);
    }

    public boolean close() {
        return varnam_close(this.handle) == VARNAM_SUCCESS;
    }

    public void setDictionarySuggestionsLimit(int limit) {
        varnam_set_dictionary_suggestions_limit(this.handle, limit);
    }

    public void setTokenizerSuggestionsLimit(int limit) {
        varnam_set_tokenizer_suggestions_limit(this.handle, limit);
    }

    public Suggestion[] transliterate(int id, String word) throws VarnamException {
        return varnam_transliterate(this.handle, id, word);
    }

    public TransliterationResult transliterateAdvanced(int id, String word) throws VarnamException {
        return varnam_transliterate_advanced(this.handle, id, word);
    }

    public boolean cancel(int id) {
        return varnam_cancel(id) == VARNAM_SUCCESS;
    }

    public void learn(String word) throws VarnamException {
        int status = varnam_learn(handle, word);
        if (status != 0) {
            throw new VarnamException(varnam_get_last_error(handle));
        }
    }

    public void unlearn(String word) throws VarnamException {
        int status = varnam_unlearn(handle, word);
        if (status != 0) {
            throw new VarnamException(varnam_get_last_error(handle));
        }
    }

    public LearnStatus learnFromFile(String path) {
        return varnam_learn_from_file(this.handle, path);
    }

    /**
     * Import a varnam exported/trained file
     * @param path
     * @return
     */
    public void importFromFile(String path) throws VarnamException {
        int status = varnam_import(this.handle, path);
        if (status != VARNAM_SUCCESS) {
            throw new VarnamException(getLastError());
        }
    }

    /**
     * Export learnings to a folder
     * @param dirPath
     * @param callback
     * @throws VarnamException
     */
    public void export(String filePath, int wordsPerFile) throws VarnamException {
        int status = varnam_export(this.handle, filePath, wordsPerFile);
        if (status != VARNAM_SUCCESS) {
            throw new VarnamException(getLastError());
        }
    }

    public Suggestion[] getRecentlyLearnedWords(int id, int offset, int limit) {
        return varnam_get_recently_learned_words(this.handle, id, offset, limit);
    }

    public static void setVSTLookupDir(String path) {
        varnam_set_vst_lookup_dir(path);
    }
    public static SchemeDetails[] getAllSchemeDetails() {
        return varnam_get_all_scheme_details();
    }
}
