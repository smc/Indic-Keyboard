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

#ifndef LATINIME_GESTURE_SUGGEST_POLICY_H
#define LATINIME_GESTURE_SUGGEST_POLICY_H

#include "defines.h"
#include "suggest/core/policy/suggest_policy.h"
#include "suggest/policyimpl/gesture/gesture_scoring.h"
#include "suggest/policyimpl/gesture/gesture_traversal.h"
#include "suggest/policyimpl/gesture/gesture_weighting.h"

namespace latinime {

class Scoring;
class Traversal;
class Weighting;

class GestureSuggestPolicy : public SuggestPolicy {
 public:
    static const GestureSuggestPolicy *getInstance() { return &sInstance; }

    GestureSuggestPolicy() {}
    virtual ~GestureSuggestPolicy() {}

    AK_FORCE_INLINE const Traversal *getTraversal() const {
        return GestureTraversal::getInstance();
    }

    AK_FORCE_INLINE const Scoring *getScoring() const {
        return GestureScoring::getInstance();
    }

    AK_FORCE_INLINE const Weighting *getWeighting() const {
        return GestureWeighting::getInstance();
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(GestureSuggestPolicy);
    static const GestureSuggestPolicy sInstance;
};
} // namespace latinime
#endif // LATINIME_GESTURE_SUGGEST_POLICY_H
