/*
 * Copyright (C) 2011 The Android Open Source Project
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

package org.smc.inputmethod.indic;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.makedict.UnsupportedFormatException;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;
import com.android.inputmethod.latin.utils.DictionaryInfoUtils;
import com.android.inputmethod.latin.utils.LocaleUtils;

/**
 * Helper class to get the address of a mmap'able dictionary file.
 */
final public class BinaryDictionaryGetter {

    /**
     * Used for Log actions from this class
     */
    private static final String TAG = BinaryDictionaryGetter.class.getSimpleName();

    /**
     * Used to return empty lists
     */
    private static final File[] EMPTY_FILE_ARRAY = new File[0];

    /**
     * Name of the common preferences name to know which word list are on and which are off.
     */
    private static final String COMMON_PREFERENCES_NAME = "LatinImeDictPrefs";

    // Name of the category for the main dictionary
    public static final String MAIN_DICTIONARY_CATEGORY = "main";
    public static final String ID_CATEGORY_SEPARATOR = ":";

    // The key considered to read the version attribute in a dictionary file.
    private static String VERSION_KEY = "version";

    // Prevents this from being instantiated
    private BinaryDictionaryGetter() {}

    /**
     * Generates a unique temporary file name in the app cache directory.
     */
    public static String getTempFileName(final String id, final Context context)
            throws IOException {
        final String safeId = DictionaryInfoUtils.replaceFileNameDangerousCharacters(id);
        final File directory = new File(DictionaryInfoUtils.getWordListTempDirectory(context));
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e(TAG, "Could not create the temporary directory");
            }
        }
        // If the first argument is less than three chars, createTempFile throws a
        // RuntimeException. We don't really care about what name we get, so just
        // put a three-chars prefix makes us safe.
        return File.createTempFile("xxx" + safeId, null, directory).getAbsolutePath();
    }

    /**
     * Returns a file address from a resource, or null if it cannot be opened.
     */
    public static AssetFileAddress loadFallbackResource(final Context context,
            final int fallbackResId) {
        final AssetFileDescriptor afd = context.getResources().openRawResourceFd(fallbackResId);
        if (afd == null) {
            Log.e(TAG, "Found the resource but cannot read it. Is it compressed? resId="
                    + fallbackResId);
            return null;
        }
        try {
            return AssetFileAddress.makeFromFileNameAndOffset(
                    context.getApplicationInfo().sourceDir, afd.getStartOffset(), afd.getLength());
        } finally {
            try {
                afd.close();
            } catch (IOException e) {
                // Ignored
            }
        }
    }

    private static final class DictPackSettings {
        final SharedPreferences mDictPreferences;
        public DictPackSettings(final Context context) {
            mDictPreferences = null == context ? null
                    : context.getSharedPreferences(COMMON_PREFERENCES_NAME,
                            Context.MODE_MULTI_PROCESS);
        }
        public boolean isWordListActive(final String dictId) {
            if (null == mDictPreferences) {
                // If we don't have preferences it basically means we can't find the dictionary
                // pack - either it's not installed, or it's disabled, or there is some strange
                // bug. Either way, a word list with no settings should be on by default: default
                // dictionaries in LatinIME are on if there is no settings at all, and if for some
                // reason some dictionaries have been installed BUT the dictionary pack can't be
                // found anymore it's safer to actually supply installed dictionaries.
                return true;
            } else {
                // The default is true here for the same reasons as above. We got the dictionary
                // pack but if we don't have any settings for it it means the user has never been
                // to the settings yet. So by default, the main dictionaries should be on.
                return mDictPreferences.getBoolean(dictId, true);
            }
        }
    }

    /**
     * Utility class for the {@link #getCachedWordLists} method
     */
    private static final class FileAndMatchLevel {
        final File mFile;
        final int mMatchLevel;
        public FileAndMatchLevel(final File file, final int matchLevel) {
            mFile = file;
            mMatchLevel = matchLevel;
        }
    }

    /**
     * Returns the list of cached files for a specific locale, one for each category.
     *
     * This will return exactly one file for each word list category that matches
     * the passed locale. If several files match the locale for any given category,
     * this returns the file with the closest match to the locale. For example, if
     * the passed word list is en_US, and for a category we have an en and an en_US
     * word list available, we'll return only the en_US one.
     * Thus, the list will contain as many files as there are categories.
     *
     * @param locale the locale to find the dictionary files for, as a string.
     * @param context the context on which to open the files upon.
     * @return an array of binary dictionary files, which may be empty but may not be null.
     */
    public static File[] getCachedWordLists(final String locale, final Context context) {
        final File[] directoryList = DictionaryInfoUtils.getCachedDirectoryList(context);
        if (null == directoryList) return EMPTY_FILE_ARRAY;
        final HashMap<String, FileAndMatchLevel> cacheFiles = new HashMap<>();
        for (File directory : directoryList) {
            if (!directory.isDirectory()) continue;
            final String dirLocale =
                    DictionaryInfoUtils.getWordListIdFromFileName(directory.getName());
            final int matchLevel = LocaleUtils.getMatchLevel(dirLocale, locale);
            if (LocaleUtils.isMatch(matchLevel)) {
                final File[] wordLists = directory.listFiles();
                if (null != wordLists) {
                    for (File wordList : wordLists) {
                        final String category =
                                DictionaryInfoUtils.getCategoryFromFileName(wordList.getName());
                        final FileAndMatchLevel currentBestMatch = cacheFiles.get(category);
                        if (null == currentBestMatch || currentBestMatch.mMatchLevel < matchLevel) {
                            cacheFiles.put(category, new FileAndMatchLevel(wordList, matchLevel));
                        }
                    }
                }
            }
        }
        if (cacheFiles.isEmpty()) return EMPTY_FILE_ARRAY;
        final File[] result = new File[cacheFiles.size()];
        int index = 0;
        for (final FileAndMatchLevel entry : cacheFiles.values()) {
            result[index++] = entry.mFile;
        }
        return result;
    }

    /**
     * Remove all files with the passed id, except the passed file.
     *
     * If a dictionary with a given ID has a metadata change that causes it to change
     * path, we need to remove the old version. The only way to do this is to check all
     * installed files for a matching ID in a different directory.
     */
    public static void removeFilesWithIdExcept(final Context context, final String id,
            final File fileToKeep) {
        try {
            final File canonicalFileToKeep = fileToKeep.getCanonicalFile();
            final File[] directoryList = DictionaryInfoUtils.getCachedDirectoryList(context);
            if (null == directoryList) return;
            for (File directory : directoryList) {
                // There is one directory per locale. See #getCachedDirectoryList
                if (!directory.isDirectory()) continue;
                final File[] wordLists = directory.listFiles();
                if (null == wordLists) continue;
                for (File wordList : wordLists) {
                    final String fileId =
                            DictionaryInfoUtils.getWordListIdFromFileName(wordList.getName());
                    if (fileId.equals(id)) {
                        if (!canonicalFileToKeep.equals(wordList.getCanonicalFile())) {
                            wordList.delete();
                        }
                    }
                }
            }
        } catch (java.io.IOException e) {
            Log.e(TAG, "IOException trying to cleanup files", e);
        }
    }

    // ## HACK ## we prevent usage of a dictionary before version 18. The reason for this is, since
    // those do not include whitelist entries, the new code with an old version of the dictionary
    // would lose whitelist functionality.
    private static boolean hackCanUseDictionaryFile(final Locale locale, final File file) {
        try {
            // Read the version of the file
            final DictionaryHeader header = BinaryDictionaryUtils.getHeader(file);
            final String version = header.mDictionaryOptions.mAttributes.get(VERSION_KEY);
            if (null == version) {
                // No version in the options : the format is unexpected
                return false;
            }
            // Version 18 is the first one to include the whitelist
            // Obviously this is a big ## HACK ##
            return Integer.parseInt(version) >= 18;
        } catch (java.io.FileNotFoundException e) {
            return false;
        } catch (java.io.IOException e) {
            return false;
        } catch (NumberFormatException e) {
            return false;
        } catch (BufferUnderflowException e) {
            return false;
        } catch (UnsupportedFormatException e) {
            return false;
        }
    }

    /**
     * Returns a list of file addresses for a given locale, trying relevant methods in order.
     *
     * Tries to get binary dictionaries from various sources, in order:
     * - Uses a content provider to get a public dictionary set, as per the protocol described
     *   in BinaryDictionaryFileDumper.
     * If that fails:
     * - Gets a file name from the built-in dictionary for this locale, if any.
     * If that fails:
     * - Returns null.
     * @return The list of addresses of valid dictionary files, or null.
     */
    public static ArrayList<AssetFileAddress> getDictionaryFiles(final Locale locale,
            final Context context) {

        final boolean hasDefaultWordList = DictionaryFactory.isDictionaryAvailable(context, locale);
        BinaryDictionaryFileDumper.cacheWordListsFromContentProvider(locale, context,
                hasDefaultWordList);
        final File[] cachedWordLists = getCachedWordLists(locale.toString(), context);
        final String mainDictId = DictionaryInfoUtils.getMainDictId(locale);
        final DictPackSettings dictPackSettings = new DictPackSettings(context);

        boolean foundMainDict = false;
        final ArrayList<AssetFileAddress> fileList = new ArrayList<>();
        // cachedWordLists may not be null, see doc for getCachedDictionaryList
        for (final File f : cachedWordLists) {
            final String wordListId = DictionaryInfoUtils.getWordListIdFromFileName(f.getName());
            final boolean canUse = f.canRead() && hackCanUseDictionaryFile(locale, f);
            if (canUse && DictionaryInfoUtils.isMainWordListId(wordListId)) {
                foundMainDict = true;
            }
            if (!dictPackSettings.isWordListActive(wordListId)) continue;
            if (canUse) {
                final AssetFileAddress afa = AssetFileAddress.makeFromFileName(f.getPath());
                if (null != afa) fileList.add(afa);
            } else {
                Log.e(TAG, "Found a cached dictionary file but cannot read or use it");
            }
        }

        if (!foundMainDict && dictPackSettings.isWordListActive(mainDictId)) {
            final int fallbackResId =
                    DictionaryInfoUtils.getMainDictionaryResourceId(context.getResources(), locale, context.getPackageName());
            final AssetFileAddress fallbackAsset = loadFallbackResource(context, fallbackResId);
            if (null != fallbackAsset) {
                fileList.add(fallbackAsset);
            }
        }

        return fileList;
    }
}
