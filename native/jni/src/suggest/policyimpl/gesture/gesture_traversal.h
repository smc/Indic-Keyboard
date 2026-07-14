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

#ifndef LATINIME_GESTURE_TRAVERSAL_H
#define LATINIME_GESTURE_TRAVERSAL_H

#include <cstdint>

#include "defines.h"
#include "suggest/core/dicnode/dic_node.h"
#include "suggest/core/dicnode/dic_node_vector.h"
#include "suggest/core/policy/traversal.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/policyimpl/gesture/gesture_alignment.h"
#include "suggest/policyimpl/gesture/gesture_params.h"

namespace latinime {

class GestureTraversal : public Traversal {
 public:
    static const GestureTraversal *getInstance() { return &sInstance; }

    // MAX_POINTER_COUNT_G doubles as the engine's isGeometric flag: without it no gesture
    // sampling or align probabilities are computed at all.
    AK_FORCE_INLINE int getMaxPointerCount() const {
        return MAX_POINTER_COUNT_G;
    }

    AK_FORCE_INLINE bool allowsErrorCorrections(const DicNode *const dicNode) const {
        return false;
    }

    // A swipe can only trace codepoints that exist as keys. Letters with no key (apostrophe,
    // hyphen) are passed through as omissions — the sole correction the gesture policy allows —
    // so "don't" is reachable from the d-o-n-t path.
    bool isOmission(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode, const DicNode *const childDicNode,
            const bool allowsErrorCorrections) const;

    AK_FORCE_INLINE bool isSpaceSubstitutionTerminal(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode) const {
        return false;
    }

    AK_FORCE_INLINE bool isSpaceOmissionTerminal(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode) const {
        return false;
    }

    AK_FORCE_INLINE bool shouldDepthLevelCache(
            const DicTraverseSession *const traverseSession) const {
        return false;
    }

    AK_FORCE_INLINE bool shouldNodeLevelCache(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode) const {
        return false;
    }

    AK_FORCE_INLINE bool canDoLookAheadCorrection(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode) const {
        return false;
    }

    AK_FORCE_INLINE ProximityType getProximityType(
            const DicTraverseSession *const traverseSession, const DicNode *const dicNode,
            const DicNode *const childDicNode) const {
        return traverseSession->getProximityTypeG(dicNode, childDicNode->getNodeCodePoint());
    }

    int getMatchAlignPointCount(const DicTraverseSession *const traverseSession,
            const DicNode *const dicNode, const DicNode *const childDicNode) const;

    // A word must explain the whole swipe: terminals ending early are charged the trailing
    // skip-sum via CT_TERMINAL_INSERTION.
    AK_FORCE_INLINE bool needsToTraverseAllUserInput() const {
        return true;
    }

    AK_FORCE_INLINE float getMaxSpatialDistance() const {
        return GestureParams::MAX_SPATIAL_DISTANCE;
    }

    AK_FORCE_INLINE int getDefaultExpandDicNodeSize() const {
        return DicNodeVector::DEFAULT_NODES_SIZE_FOR_OPTIMIZATION;
    }

    AK_FORCE_INLINE int getMaxCacheSize(const int inputSize, const float weightForLocale) const {
        return GestureParams::BEAM_SIZE;
    }

    AK_FORCE_INLINE int getTerminalCacheSize() const {
        return MAX_RESULTS;
    }

    AK_FORCE_INLINE bool isPossibleOmissionChildNode(
            const DicTraverseSession *const traverseSession, const DicNode *const parentDicNode,
            const DicNode *const dicNode) const {
        return true;
    }

    AK_FORCE_INLINE bool isGoodToTraverseNextWord(const DicNode *const dicNode,
            const int probability) const {
        return false;
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(GestureTraversal);
    static const GestureTraversal sInstance;

    GestureTraversal() {}
    ~GestureTraversal() {}
};
} // namespace latinime
#endif // LATINIME_GESTURE_TRAVERSAL_H
