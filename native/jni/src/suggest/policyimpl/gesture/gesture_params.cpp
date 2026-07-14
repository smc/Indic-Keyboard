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

#include "suggest/policyimpl/gesture/gesture_params.h"

namespace latinime {

const float GestureParams::ALIGN_WEIGHT = 1.0f;
const float GestureParams::LM_WEIGHT = 6.0f;
const float GestureParams::DOUBLE_LETTER_COST = 0.15f;
const float GestureParams::KEYLESS_PASS_COST = 0.35f;
const int GestureParams::MAX_ALIGN_CANDIDATES = 3;
const int GestureParams::BEAM_SIZE = 240;
const float GestureParams::MAX_SPATIAL_DISTANCE = 1.0f;
const float GestureParams::BASE_OUTPUT_SCORE = 1.0f;
const float GestureParams::OUTPUT_SCORE_PER_SAMPLE = 0.5f;
} // namespace latinime
