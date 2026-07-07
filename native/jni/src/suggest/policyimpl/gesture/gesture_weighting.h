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

// The gesture decoder core. A hypothesis explains every sampled point of the swipe exactly
// once: each matched character consumes its align sample plus the skip cost of every sample
// jumped over, and a terminal pays the skip-sum of whatever remains. Align and skip costs are
// -log probabilities from ProximityInfoState's per-sample distribution, so they are directly
// additive. The beam explores several candidate alignments per matched character (see
// GestureTraversal::getMatchAlignPointCount and the rank clones made in suggest.cpp), which is
// what makes the search a global alignment rather than a greedy one.

#ifndef LATINIME_GESTURE_WEIGHTING_H
#define LATINIME_GESTURE_WEIGHTING_H

#include "defines.h"
#include "suggest/core/dictionary/error_type_utils.h"
#include "suggest/core/policy/weighting.h"

namespace latinime {

class DicNode;
struct DicNode_InputStateG;
class DicTraverseSession;
class MultiBigramMap;

class GestureWeighting : public Weighting {
 public:
    static const GestureWeighting *getInstance() { return &sInstance; }

 protected:
    float getMatchedCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode, DicNode_InputStateG *inputStateG) const;

    float getTerminalInsertionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const;

    float getTerminalLanguageCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode, float dicNodeLanguageImprobability) const;

    AK_FORCE_INLINE float getTerminalSpatialCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const {
        return 0.0f;
    }

    AK_FORCE_INLINE float getOmissionCost(const DicNode *const parentDicNode,
            const DicNode *const dicNode) const {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }

    AK_FORCE_INLINE bool isProximityDicNode(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const {
        return false;
    }

    AK_FORCE_INLINE float getTranspositionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const parentDicNode, const DicNode *const dicNode) const {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }

    AK_FORCE_INLINE float getInsertionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const parentDicNode, const DicNode *const dicNode) const {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }

    AK_FORCE_INLINE float getSpaceOmissionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode, DicNode_InputStateG *const inputStateG) const {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }

    AK_FORCE_INLINE float getNewWordBigramLanguageCost(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode,
            MultiBigramMap *const multiBigramMap) const {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }

    float getCompletionCost(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const;

    // Beam pruning compares cost per consumed sample; hypotheses at the same trie depth may
    // have consumed very different sample counts.
    AK_FORCE_INLINE bool needsToNormalizeCompoundDistance() const {
        return true;
    }

    AK_FORCE_INLINE float getAdditionalProximityCost() const {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }

    AK_FORCE_INLINE float getSubstitutionCost() const {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }

    AK_FORCE_INLINE float getSpaceSubstitutionCost(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode) const {
        return static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    }

    ErrorTypeUtils::ErrorType getErrorType(const CorrectionType correctionType,
            const DicTraverseSession *const traverseSession,
            const DicNode *const parentDicNode, const DicNode *const dicNode) const;

 private:
    DISALLOW_COPY_AND_ASSIGN(GestureWeighting);
    static const GestureWeighting sInstance;

    GestureWeighting() {}
    ~GestureWeighting() {}
};
} // namespace latinime
#endif // LATINIME_GESTURE_WEIGHTING_H
