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

#include "suggest/policyimpl/gesture/gesture_alignment.h"

#include <algorithm>

#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/layout/proximity_info_state.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/policyimpl/gesture/gesture_params.h"
#include "utils/char_utils.h"

namespace latinime {

/* static */ bool GestureAlignment::isSingleGesturePointer(
        const DicTraverseSession *const traverseSession) {
    int pointerId = 0;
    return traverseSession->isOnlyOnePointerUsed(&pointerId) && pointerId == 0;
}

/* static */ int GestureAlignment::enumerateAlignCandidates(
        const DicTraverseSession *const traverseSession, const int fromSampleIndex,
        const int codePoint, int *const outCandidateSamples) {
    const ProximityInfoState *const pInfoState = traverseSession->getProximityInfoState(0);
    const int sampledSize = pInfoState->size();
    if (fromSampleIndex >= sampledSize) {
        return 0;
    }
    const int keyIndex = traverseSession->getProximityInfo()->getKeyIndexOf(
            CharUtils::toBaseLowerCase(codePoint));
    if (keyIndex == NOT_AN_INDEX) {
        return 0;
    }
    const float maxCost = static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    float candidateCosts[GestureParams::MAX_ALIGN_CANDIDATES];
    int count = 0;
    float prevCost = maxCost;
    for (int j = fromSampleIndex; j < sampledSize; ++j) {
        const float cost = pInfoState->getProbability(j, keyIndex);
        const float nextCost = (j + 1 < sampledSize)
                ? pInfoState->getProbability(j + 1, keyIndex) : maxCost;
        // Local minimum of the align-cost curve; plateaus keep only their first sample.
        if (cost < maxCost && cost < prevCost && cost <= nextCost) {
            int pos = count;
            while (pos > 0 && candidateCosts[pos - 1] > cost) {
                --pos;
            }
            if (pos < GestureParams::MAX_ALIGN_CANDIDATES) {
                const int last = std::min(count, GestureParams::MAX_ALIGN_CANDIDATES - 1);
                for (int m = last; m > pos; --m) {
                    candidateCosts[m] = candidateCosts[m - 1];
                    outCandidateSamples[m] = outCandidateSamples[m - 1];
                }
                candidateCosts[pos] = cost;
                outCandidateSamples[pos] = j;
                if (count < GestureParams::MAX_ALIGN_CANDIDATES) {
                    ++count;
                }
            }
        }
        prevCost = cost;
    }
    return count;
}

/* static */ float GestureAlignment::sumSkipCosts(const ProximityInfoState *const pInfoState,
        const int fromSampleIndex, const int endSampleIndex) {
    const float maxCost = static_cast<float>(MAX_VALUE_FOR_WEIGHTING);
    float sum = 0.0f;
    for (int k = fromSampleIndex; k < endSampleIndex; ++k) {
        const float skipCost = pInfoState->getProbability(k, NOT_AN_INDEX);
        if (skipCost >= maxCost) {
            return maxCost;
        }
        sum += skipCost;
    }
    return sum;
}

/* static */ bool GestureAlignment::isDoubleLetterRetry(const DicNode *const dicNode) {
    // The previous code point is base-lowered when our weighting recorded it, but raw when the
    // engine's +1 forward path did (e.g. after a CT_COMPLETION advance) — fold both sides.
    const int prevCodePoint = dicNode->getPrevCodePointG(0);
    if (prevCodePoint <= 0) {
        return false;
    }
    return CharUtils::toBaseLowerCase(dicNode->getNodeCodePoint())
            == CharUtils::toBaseLowerCase(prevCodePoint);
}
} // namespace latinime
