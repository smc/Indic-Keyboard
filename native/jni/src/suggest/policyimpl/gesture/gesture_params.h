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

#ifndef LATINIME_GESTURE_PARAMS_H
#define LATINIME_GESTURE_PARAMS_H

#include "defines.h"

namespace latinime {

// Tuned against the host replay harness (native/host-harness), not hand-guessed; see
// gesturetyping-design.md. ALIGN_WEIGHT is 1.0 by construction: align and skip costs are
// -log probabilities split from the same per-sample distribution.
class GestureParams {
 public:
    static const float ALIGN_WEIGHT;
    static const float LM_WEIGHT;
    static const float DOUBLE_LETTER_COST;
    static const float KEYLESS_PASS_COST;
    static const float MISSED_LETTER_BASE_COST;
    static const float MISSED_LETTER_DISTANCE_COST;
    static const float SKIP_COST_CAP;
    static const float SKIP_COST_WEIGHT;
    static const float END_ANCHOR_COST;
    static const int MAX_ALIGN_CANDIDATES;
    static const int BEAM_SIZE;
    static const float MAX_SPATIAL_DISTANCE;
    static const float BASE_OUTPUT_SCORE;
    static const float OUTPUT_SCORE_PER_SAMPLE;

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(GestureParams);
};
} // namespace latinime
#endif // LATINIME_GESTURE_PARAMS_H
