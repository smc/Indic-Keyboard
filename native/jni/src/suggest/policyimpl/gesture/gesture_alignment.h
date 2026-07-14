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

#ifndef LATINIME_GESTURE_ALIGNMENT_H
#define LATINIME_GESTURE_ALIGNMENT_H

#include "defines.h"

namespace latinime {

class DicNode;
class DicTraverseSession;
class ProximityInfoState;

// Shared between GestureTraversal (candidate count) and GestureWeighting (candidate pick);
// the enumeration must be deterministic and identical on both sides, since the traversal's
// count decides how many beam clones exist and each clone's rank selects one candidate here.
class GestureAlignment {
 public:
    // The gesture policy only decodes single-stroke input on pointer 0; everything it does
    // indexes pointer-0 sample arrays whose bounds checks are debug-only.
    static bool isSingleGesturePointer(const DicTraverseSession *const traverseSession);

    // Candidate align samples for codePoint at or after fromSampleIndex: local minima of the
    // -log(P) align cost, ordered by ascending cost (ties: earlier sample first), at most
    // GestureParams::MAX_ALIGN_CANDIDATES of them. Returns the count.
    static int enumerateAlignCandidates(const DicTraverseSession *const traverseSession,
            const int fromSampleIndex, const int codePoint, int *const outCandidateSamples);

    // Sum of per-sample skip costs over [fromSampleIndex, endSampleIndex);
    // MAX_VALUE_FOR_WEIGHTING if any sample in the range is unskippable.
    static float sumSkipCosts(const ProximityInfoState *const pInfoState,
            const int fromSampleIndex, const int endSampleIndex);

    // Rescue for a letter whose key is outside every remaining sample's near set (a badly
    // cut corner): the geometrically closest sample. Returns the center distance in
    // key-width units and sets *outSampleIndex, or a negative value when impossible.
    static float findFallbackAlignment(const DicTraverseSession *const traverseSession,
            const int fromSampleIndex, const int codePoint, int *const outSampleIndex);

    // A trie child repeating the letter the node just matched ("hello", "feel"): decoded as a
    // zero-consume re-match of the same alignment rather than a fresh key crossing.
    static bool isDoubleLetterRetry(const DicNode *const dicNode);

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(GestureAlignment);
};
} // namespace latinime
#endif // LATINIME_GESTURE_ALIGNMENT_H
