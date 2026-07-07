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

#ifndef LATINIME_GESTURE_SCORING_H
#define LATINIME_GESTURE_SCORING_H

#include "defines.h"
#include "suggest/core/dictionary/error_type_utils.h"
#include "suggest/core/policy/scoring.h"
#include "suggest/policyimpl/gesture/gesture_params.h"

namespace latinime {

class DicNode;
class DicTraverseSession;

class GestureScoring : public Scoring {
 public:
    static const GestureScoring *getInstance() { return &sInstance; }

    // Monotone decreasing in compound distance; inputSize (the sample count) is shared by all
    // candidates of one swipe, so the denominator only sets the output scale.
    AK_FORCE_INLINE int calculateFinalScore(const float compoundDistance, const int inputSize,
            const ErrorTypeUtils::ErrorType containedErrorTypes, const bool forceCommit,
            const bool boostExactMatches, const bool hasProbabilityZero) const {
        const float maxDistance = GestureParams::LM_WEIGHT
                + static_cast<float>(inputSize) * GestureParams::OUTPUT_SCORE_PER_SAMPLE;
        const float score = GestureParams::BASE_OUTPUT_SCORE - compoundDistance / maxDistance;
        return static_cast<int>(score * SUGGEST_INTERFACE_OUTPUT_SCALE);
    }

    AK_FORCE_INLINE void getMostProbableString(const DicTraverseSession *const traverseSession,
            const float weightOfLangModelVsSpatialModel,
            SuggestionResults *const outSuggestionResults) const {
    }

    AK_FORCE_INLINE float getAdjustedWeightOfLangModelVsSpatialModel(
            DicTraverseSession *const traverseSession, DicNode *const terminals,
            const int size) const {
        return 1.0f;
    }

    AK_FORCE_INLINE float getDoubleLetterDemotionDistanceCost(
            const DicNode *const terminalDicNode) const {
        return 0.0f;
    }

    // For gesture, inputSize is the sample count, so the engine's multi-word force-commit
    // heuristic (inputSize >= 16) would fire on nearly every swipe.
    AK_FORCE_INLINE bool autoCorrectsToMultiWordSuggestionIfTop() const {
        return false;
    }

    AK_FORCE_INLINE bool sameAsTyped(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode) const {
        return false;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(GestureScoring);
    static const GestureScoring sInstance;

    GestureScoring() {}
    ~GestureScoring() {}
};
} // namespace latinime
#endif // LATINIME_GESTURE_SCORING_H
