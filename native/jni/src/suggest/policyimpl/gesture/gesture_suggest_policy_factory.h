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

#ifndef LATINIME_GESTURE_SUGGEST_POLICY_FACTORY_H
#define LATINIME_GESTURE_SUGGEST_POLICY_FACTORY_H

#include "defines.h"
#include "suggest/policyimpl/gesture/gesture_suggest_policy.h"

namespace latinime {

class SuggestPolicy;

// AOSP shipped this as an unset function-pointer hook (Google's gesture decoder registered
// itself at runtime and was never open-sourced); our policy registers at compile time like
// the typing one — Suggest caches the policy pointers at construction.
class GestureSuggestPolicyFactory {
 public:
    static const SuggestPolicy *getGestureSuggestPolicy() {
        return GestureSuggestPolicy::getInstance();
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(GestureSuggestPolicyFactory);
};
} // namespace latinime
#endif // LATINIME_GESTURE_SUGGEST_POLICY_FACTORY_H
