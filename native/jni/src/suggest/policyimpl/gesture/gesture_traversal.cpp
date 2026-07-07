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

#include "suggest/policyimpl/gesture/gesture_traversal.h"

namespace latinime {

const GestureTraversal GestureTraversal::sInstance;

int GestureTraversal::getMatchAlignPointCount(const DicTraverseSession *const traverseSession,
        const DicNode *const dicNode, const DicNode *const childDicNode) const {
    if (!GestureAlignment::isSingleGesturePointer(traverseSession)) {
        return 0;
    }
    int candidateSamples[GestureParams::MAX_ALIGN_CANDIDATES];
    const int candidateCount = GestureAlignment::enumerateAlignCandidates(traverseSession,
            childDicNode->getInputIndex(0), childDicNode->getNodeCodePoint(), candidateSamples);
    const int doubleLetterCount = GestureAlignment::isDoubleLetterRetry(childDicNode) ? 1 : 0;
    return doubleLetterCount + candidateCount;
}
} // namespace latinime
