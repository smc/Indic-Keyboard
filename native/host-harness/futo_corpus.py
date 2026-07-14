#!/usr/bin/env python3
# Copyright 2026, Jishnu Mohan <jishnu7@gmail.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Convert the FUTO swipe dataset (huggingface.co/datasets/futo-org/swipe.futo.org)
into the replay-harness corpus format.

FUTO records carry normalized [0,1] coordinates over the letter-key area plus a
matching normalized layout file, so points and keys are mapped through one
canonical canvas without geometric error; only the physical aspect ratio is
canonicalized. The Java-side pre-sampling rule (drop points closer than
mostCommonKeyWidth/6 to the last kept point) is applied here, mirroring
GestureStrokeRecognitionPoints.
"""

import argparse
import json
import math
import sys

CANVAS_WIDTH = 1080
# Median portrait canvas aspect (h/w) observed in the dataset.
CANVAS_HEIGHT = round(1080 * 0.4194)

STRIP_CHARS = ".,!?;:\"()[]“”‘’"


def load_geometry(layout_path):
    """Returns (geometry, layout name)."""
    with open(layout_path) as f:
        layout = json.load(f)
    keys = []
    widths = []
    heights = []
    for k in layout["keys"]:
        w = 2 * k["rx"] * CANVAS_WIDTH
        h = 2 * k["ry"] * CANVAS_HEIGHT
        keys.append({
            "code": ord(k["letter"]),
            "x": round((k["cx"] - k["rx"]) * CANVAS_WIDTH),
            "y": round((k["cy"] - k["ry"]) * CANVAS_HEIGHT),
            "w": round(w),
            "h": round(h),
        })
        widths.append(w)
        heights.append(h)
    return {
        "width": CANVAS_WIDTH,
        "height": CANVAS_HEIGHT,
        "mostCommonKeyWidth": round(sorted(widths)[len(widths) // 2]),
        "mostCommonKeyHeight": round(sorted(heights)[len(heights) // 2]),
        "keys": keys,
    }, layout.get("name", "qwerty")


def clean_word(word):
    return word.strip(STRIP_CHARS)


def is_swipeable(word):
    return len(word) >= 2 and all(c.isalpha() or c in "'-" for c in word) \
            and any(c.isalpha() for c in word)


def convert_record(rec, min_sample_dist, layout_name, language):
    # swipe-5 mixes layouts, languages and nintype-style two-finger swipes (dict-shaped
    # "data"); earlier collections carry none of these fields.
    if rec.get("layout", layout_name) != layout_name:
        return None
    if rec.get("language", language) != language:
        return None
    if rec.get("dual_finger") or not isinstance(rec["data"], list):
        return None
    if not rec["orientation"].startswith("portrait"):
        return None
    if rec["distance"] >= 100000:
        return None
    word = clean_word(rec["word"])
    if not is_swipeable(word):
        return None
    data = rec["data"]
    if len(data) < 4:
        return None
    t0 = data[0]["t"]
    xs, ys, times = [], [], []
    last = None
    for p in data:
        x = min(max(p["x"], 0.0), 1.0) * (CANVAS_WIDTH - 1)
        y = min(max(p["y"], 0.0), 1.0) * (CANVAS_HEIGHT - 1)
        if last is not None and math.hypot(x - last[0], y - last[1]) < min_sample_dist:
            continue
        t = max(p["t"] - t0, times[-1] if times else 0)
        xs.append(round(x))
        ys.append(round(y))
        times.append(t)
        last = (x, y)
    if len(xs) < 2:
        return None
    case = {"word": word, "xs": xs, "ys": ys, "times": times,
            "distance": rec["distance"]}
    idx = rec.get("word_idx")
    tokens = rec.get("sentence", "").split()
    if idx == 0:
        case["bos"] = 1
    elif isinstance(idx, int) and 0 < idx < len(tokens):
        prev = clean_word(tokens[idx - 1])
        if prev:
            case["prev"] = prev
    return case


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("jsonl", help="FUTO dataset .jsonl file")
    ap.add_argument("--layout", required=True, help="FUTO layout json (qwerty.json)")
    ap.add_argument("--language", default="en",
                    help="keep only records in this language (default en)")
    ap.add_argument("--max", type=int, default=0, help="cap converted cases (0 = all)")
    ap.add_argument("--out", required=True, help="output corpus JSON path")
    args = ap.parse_args()

    geometry, layout_name = load_geometry(args.layout)
    min_sample_dist = geometry["mostCommonKeyWidth"] / 6.0
    cases = []
    total = 0
    with open(args.jsonl) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            total += 1
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue
            case = convert_record(rec, min_sample_dist, layout_name, args.language)
            if case is None:
                continue
            cases.append(case)
            if args.max and len(cases) >= args.max:
                break

    with open(args.out, "w") as f:
        f.write(json.dumps({"geometry": geometry, "cases": cases},
                separators=(",", ":")))
    print(f"{len(cases)} cases (from {total} records) -> {args.out}",
          file=sys.stderr)


if __name__ == "__main__":
    main()
