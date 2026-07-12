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

import android.content.res.Resources

import androidx.core.os.ConfigurationCompat

import com.android.inputmethod.annotations.UsedForTesting
import com.android.inputmethod.keyboard.internal.MoreKeySpec
import com.android.inputmethod.latin.PunctuationSuggestions
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.StringUtils

import java.util.Arrays
import java.util.Locale

class SpacingAndPunctuations private constructor(
    private val mSortedSymbolsPrecededBySpace: IntArray,
    private val mSortedSymbolsFollowedBySpace: IntArray,
    private val mSortedSymbolsClusteringTogether: IntArray,
    private val mSortedWordConnectors: IntArray,
    @JvmField val mSortedWordSeparators: IntArray,
    private val mSortedSentenceTerminators: IntArray,
    @JvmField val mSuggestPuncList: PunctuationSuggestions,
    private val mSentenceSeparator: Int,
    private val mAbbreviationMarker: Int,
    @JvmField val mSentenceSeparatorAndSpace: String,
    @JvmField val mCurrentLanguageHasSpaces: Boolean,
    @JvmField val mUsesAmericanTypography: Boolean,
    @JvmField val mUsesGermanRules: Boolean
) {
    constructor(res: Resources) : this(
        // To be able to binary-search the code point (see isUsuallyPrecededBySpace / etc.).
        StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_preceded_by_space)),
        StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_followed_by_space)),
        StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_clustering_together)),
        StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_word_connectors)),
        StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_word_separators)),
        StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_sentence_terminators)),
        PunctuationSuggestions.newPunctuationSuggestions(
            MoreKeySpec.splitKeySpecs(res.getString(R.string.suggested_punctuations))
        ),
        res.getInteger(R.integer.sentence_separator),
        res.getInteger(R.integer.abbreviation_marker),
        String(
            intArrayOf(res.getInteger(R.integer.sentence_separator), Constants.CODE_SPACE), 0, 2
        ),
        res.getBoolean(R.bool.current_language_has_spaces),
        // Heuristic: American Typography rules are the most common across English variants;
        // German rules (not "German typography") also have small gotchas.
        Locale.ENGLISH.language == localeOf(res).language,
        Locale.GERMAN.language == localeOf(res).language
    )

    @UsedForTesting
    constructor(model: SpacingAndPunctuations, overrideSortedWordSeparators: IntArray) : this(
        model.mSortedSymbolsPrecededBySpace,
        model.mSortedSymbolsFollowedBySpace,
        model.mSortedSymbolsClusteringTogether,
        model.mSortedWordConnectors,
        overrideSortedWordSeparators,
        model.mSortedSentenceTerminators,
        model.mSuggestPuncList,
        model.mSentenceSeparator,
        model.mAbbreviationMarker,
        model.mSentenceSeparatorAndSpace,
        model.mCurrentLanguageHasSpaces,
        model.mUsesAmericanTypography,
        model.mUsesGermanRules
    )

    fun isWordSeparator(code: Int): Boolean =
        Arrays.binarySearch(mSortedWordSeparators, code) >= 0

    fun isWordConnector(code: Int): Boolean =
        Arrays.binarySearch(mSortedWordConnectors, code) >= 0

    fun isWordCodePoint(code: Int): Boolean = Character.isLetter(code) || isWordConnector(code)

    fun isUsuallyPrecededBySpace(code: Int): Boolean =
        Arrays.binarySearch(mSortedSymbolsPrecededBySpace, code) >= 0

    fun isUsuallyFollowedBySpace(code: Int): Boolean =
        Arrays.binarySearch(mSortedSymbolsFollowedBySpace, code) >= 0

    fun isClusteringSymbol(code: Int): Boolean =
        Arrays.binarySearch(mSortedSymbolsClusteringTogether, code) >= 0

    fun isSentenceTerminator(code: Int): Boolean =
        Arrays.binarySearch(mSortedSentenceTerminators, code) >= 0

    fun isAbbreviationMarker(code: Int): Boolean = code == mAbbreviationMarker

    fun isSentenceSeparator(code: Int): Boolean = code == mSentenceSeparator

    fun dump(): String = buildString {
        append("mSortedSymbolsPrecededBySpace = ${mSortedSymbolsPrecededBySpace.contentToString()}")
        append("\n   mSortedSymbolsFollowedBySpace = ${mSortedSymbolsFollowedBySpace.contentToString()}")
        append("\n   mSortedWordConnectors = ${mSortedWordConnectors.contentToString()}")
        append("\n   mSortedWordSeparators = ${mSortedWordSeparators.contentToString()}")
        append("\n   mSuggestPuncList = $mSuggestPuncList")
        append("\n   mSentenceSeparator = $mSentenceSeparator")
        append("\n   mSentenceSeparatorAndSpace = $mSentenceSeparatorAndSpace")
        append("\n   mCurrentLanguageHasSpaces = $mCurrentLanguageHasSpaces")
        append("\n   mUsesAmericanTypography = $mUsesAmericanTypography")
        append("\n   mUsesGermanRules = $mUsesGermanRules")
    }
}

private fun localeOf(res: Resources): Locale =
    ConfigurationCompat.getLocales(res.configuration).get(0) ?: Locale.getDefault()
