/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include "suggest/policyimpl/gesture/gesture_weighting.h"

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/layout/proximity_info_state.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/policyimpl/gesture/gesture_alignment.h"
#include "suggest/policyimpl/gesture/gesture_params.h"
#include "utils/char_utils.h"

namespace latinime {

const GestureWeighting GestureWeighting::sInstance;

float GestureWeighting::getMatchedCost(const DicTraverseSession *const traverseSession,
        const DicNode *const dicNode, DicNode_InputStateG *inputStateG) const {
    const float maxCost = static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    if (!GestureAlignment::isSingleGesturePointer(traverseSession)) {
        return maxCost;
    }
    const ProximityInfoState *const pInfoState = traverseSession->getProximityInfoState(0);
    const int fromIndex = dicNode->getInputIndex(0);
    const int baseLowerCodePoint = CharUtils::toBaseLowerCase(dicNode->getNodeCodePoint());
    int rank = dicNode->getGestureAlignRank();
    if (GestureAlignment::isDoubleLetterRetry(dicNode)) {
        if (rank == 0) {
            inputStateG->mNeedsToUpdateInputStateG = true;
            inputStateG->mPointerId = 0;
            inputStateG->mInputIndex = fromIndex;
            inputStateG->mPrevCodePoint = baseLowerCodePoint;
            inputStateG->mDoubleLetterLevel = A_DOUBLE_LETTER;
            return GestureParams::DOUBLE_LETTER_COST;
        }
        --rank;
    }
    int candidateSamples[GestureParams::MAX_ALIGN_CANDIDATES];
    const int candidateCount = GestureAlignment::enumerateAlignCandidates(traverseSession,
            fromIndex, dicNode->getNodeCodePoint(), candidateSamples);
    // Also reached with rank 0 and no candidates via the digraph branch, which bypasses the
    // traversal's align-point count; the MAX cost is what kills those nodes.
    if (rank < 0 || rank >= candidateCount) {
        return maxCost;
    }
    const int alignSampleIndex = candidateSamples[rank];
    const float skipCost = GestureAlignment::sumSkipCosts(pInfoState, fromIndex,
            alignSampleIndex);
    if (skipCost >= maxCost) {
        return maxCost;
    }
    const int keyIndex = traverseSession->getProximityInfo()->getKeyIndexOf(baseLowerCodePoint);
    const float alignCost = pInfoState->getProbability(alignSampleIndex, keyIndex);
    inputStateG->mNeedsToUpdateInputStateG = true;
    inputStateG->mPointerId = 0;
    inputStateG->mInputIndex = alignSampleIndex + 1;
    inputStateG->mPrevCodePoint = baseLowerCodePoint;
    return skipCost + GestureParams::ALIGN_WEIGHT * alignCost;
}

float GestureWeighting::getTerminalInsertionCost(const DicTraverseSession *const traverseSession,
        const DicNode *const dicNode) const {
    if (!GestureAlignment::isSingleGesturePointer(traverseSession)) {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }
    const ProximityInfoState *const pInfoState = traverseSession->getProximityInfoState(0);
    const int fromIndex = dicNode->getInputIndex(0);
    const int sampledSize = pInfoState->size();
    if (fromIndex >= sampledSize) {
        return 0.0f;
    }
    return GestureAlignment::sumSkipCosts(pInfoState, fromIndex, sampledSize);
}

float GestureWeighting::getTerminalLanguageCost(const DicTraverseSession *const traverseSession,
        const DicNode *const dicNode, float dicNodeLanguageImprobability) const {
    return dicNodeLanguageImprobability * GestureParams::LM_WEIGHT;
}

// Reached only once every sample is consumed (isCompletion bypasses the proximity switch and
// the align-point hook). A swipe defines the whole word, so completions are impossible — with
// one legitimate exception: a word-final double letter whose first of the pair aligned at the
// last sample ("call", "too", "see" — the second letter has no sample left to consume).
float GestureWeighting::getCompletionCost(const DicTraverseSession *const traverseSession,
        const DicNode *const dicNode) const {
    return GestureAlignment::isDoubleLetterRetry(dicNode)
            ? GestureParams::DOUBLE_LETTER_COST
            : static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
}

ErrorTypeUtils::ErrorType GestureWeighting::getErrorType(const CorrectionType correctionType,
        const DicTraverseSession *const traverseSession, const DicNode *const parentDicNode,
        const DicNode *const dicNode) const {
    switch (correctionType) {
        case CT_MATCH:
        case CT_TERMINAL:
            return ErrorTypeUtils::NOT_AN_ERROR;
        // Not an edit for gesture: nearly every swipe leaves trailing samples unconsumed, and
        // EDIT_CORRECTION here would push all candidates out of the exact-match class.
        case CT_TERMINAL_INSERTION:
            return ErrorTypeUtils::NOT_AN_ERROR;
        // The only survivable completion is the word-final double letter (getCompletionCost);
        // the COMPLETION error type would demote it out of the exact-match class, where
        // DicNode::compare evicts it from the terminal queue regardless of score.
        case CT_COMPLETION:
            return ErrorTypeUtils::NOT_AN_ERROR;
        default:
            return ErrorTypeUtils::EDIT_CORRECTION;
    }
}
} // namespace latinime
