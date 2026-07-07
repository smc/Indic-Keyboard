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

"""Synthetic swipe corpus generator for the host gesture replay harness.

For each word: polyline through key centers -> corner-cut smoothing (quadratic
near vertices) -> Fitts-style speed profile at 90 Hz (dwell near letter targets,
fast transit) -> Gaussian jitter -> the Java-side pre-sampling rule (drop points
closer than mostCommonKeyWidth/6 to the last kept point, mirroring
GestureStrokeRecognitionPoints). Emits the corpus JSON consumed by gesture-replay.
"""

import argparse
import json
import math
import random
import sys

KEYBOARD_WIDTH = 1080
KEY_WIDTH = 108
KEY_HEIGHT = 220
ROWS = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
ROW_OFFSETS = [0, 54, 162]
# Letters + a bottom (space) row, like a real layout dump.
KEYBOARD_HEIGHT = 4 * KEY_HEIGHT

SAMPLE_HZ = 90
SLOW_SPEED = 0.3   # keyWidth per frame, near letter targets
FAST_SPEED = 1.2   # keyWidth per frame, in transit
CUT_RADIUS = 0.3   # keyWidth, corner-cut radius at each vertex
JITTER_SIGMA = 0.38  # key radii (keyWidth / 2)

DEFAULT_WORDS = """
the of and to in is you that it was for on are as with his they at be this
have from one had by word but not what all were when your can said there use
an each which she how their will up other about out many then them these so
some her would make like him into time has look two more write go see number
way could people my than first water been call who its now find long down day
did get come made may part over new sound take only little work know place
year live me back give most very after thing our just name good man think say
great where help through much before line right too mean old any same tell
boy follow came want show also around form three small set put end does
another well large must big even such because turn here why ask went men read
need land different home us move try kind hand picture again change off play
spell air away animal house point page letter mother answer found study still
learn should world high every near add food between own below country plant
last school father keep tree never start city earth eye light thought head
under story saw left few while along might close something seem next hard
open example begin life always those both paper together got group often run
hello feel soon see good book cool week room moon tool pool wall tall ball
fall bell wall will call small sorry happy coffee pretty bottle middle summer
dinner button yellow street sleep sweet green free agree seen queen speed
sheet feet meet deep carry marry berry funny penny puppy less miss class
grass press dress kiss cross floor door poor wood foot been
wore pore tore tire wire quit quite quiet write wrote trip tip top pot pit
pet pie tie toe toy yet wet wit rope ripe riot true power tower typewriter
property pepper pour tour route trout poet root
sad dad gas glad flag hall dash sash gash flash shall salad glass
cat dog bed red yes ten six car bus bag map cap cup arm leg ear sun son sea
eat ate pen pan fly cry dry sky buy key wet win won war law low row
keyboard facebook wonderful beautiful important different computer remember
question business children actually probably everything anything tomorrow
yesterday birthday understand information experience government available
definitely absolutely interesting restaurant appreciate development
environment communication congratulations technology television telephone
chocolate breakfast afternoon community education knowledge language mountain
building morning evening special weather whether percent perfect problem
program project purpose quickly
""".split()


def build_keys():
    keys = []
    for r, row in enumerate(ROWS):
        for i, ch in enumerate(row):
            keys.append({
                "code": ord(ch),
                "x": ROW_OFFSETS[r] + i * KEY_WIDTH,
                "y": r * KEY_HEIGHT,
                "w": KEY_WIDTH,
                "h": KEY_HEIGHT,
            })
    return keys


KEYS = build_keys()
KEY_BY_CHAR = {chr(k["code"]): k for k in KEYS}


def geometry_json():
    return {
        "width": KEYBOARD_WIDTH,
        "height": KEYBOARD_HEIGHT,
        "mostCommonKeyWidth": KEY_WIDTH,
        "mostCommonKeyHeight": KEY_HEIGHT,
        "keys": KEYS,
    }


def key_center(ch):
    k = KEY_BY_CHAR.get(ch)
    if k is None:
        return None
    return (k["x"] + k["w"] / 2.0, k["y"] + k["h"] / 2.0)


def word_vertices(word):
    """Key centers for the word's letters, collapsing consecutive duplicates
    (a swipe passes through a double letter's key once)."""
    verts = []
    prev = None
    for ch in word.lower():
        if ch == prev:
            continue
        c = key_center(ch)
        if c is None:
            return None
        verts.append(c)
        prev = ch
    return verts


def dist(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def smooth_path(verts, cut_radius):
    """Corner cutting: at each interior vertex, replace the corner with a
    quadratic Bezier whose control point is the vertex itself."""
    if len(verts) < 3:
        return list(verts)
    pts = [verts[0]]
    for i in range(1, len(verts) - 1):
        p, v, n = verts[i - 1], verts[i], verts[i + 1]
        d_in, d_out = dist(p, v), dist(v, n)
        if d_in <= 0 or d_out <= 0:
            continue
        r_in = min(cut_radius, d_in / 2.0)
        r_out = min(cut_radius, d_out / 2.0)
        a = lerp(v, p, r_in / d_in)
        b = lerp(v, n, r_out / d_out)
        pts.append(a)
        for step in range(1, 12):
            t = step / 12.0
            q0 = lerp(a, v, t)
            q1 = lerp(v, b, t)
            pts.append(lerp(q0, q1, t))
        pts.append(b)
    pts.append(verts[-1])
    return pts


def densify(pts, step=4.0):
    if len(pts) < 2:
        return list(pts)
    out = [pts[0]]
    for i in range(1, len(pts)):
        seg = dist(pts[i - 1], pts[i])
        if seg <= 0:
            continue
        n = max(1, int(seg / step))
        for j in range(1, n + 1):
            out.append(lerp(pts[i - 1], pts[i], j / n))
    return out


def speed_profile(dense, verts, key_width):
    """Walk the dense path at 90 Hz with a Fitts-style profile: slow near the
    letter targets, fast in transit."""
    if len(dense) < 2:
        x, y = dense[0]
        frame_ms = 1000.0 / SAMPLE_HZ
        return [(x, y, i * frame_ms) for i in range(8)]
    cum = [0.0]
    for i in range(1, len(dense)):
        cum.append(cum[-1] + dist(dense[i - 1], dense[i]))
    total = cum[-1]

    def point_at(s):
        lo, hi = 0, len(cum) - 1
        while lo < hi:
            mid = (lo + hi) // 2
            if cum[mid] < s:
                lo = mid + 1
            else:
                hi = mid
        i = max(1, lo)
        seg = cum[i] - cum[i - 1]
        t = (s - cum[i - 1]) / seg if seg > 0 else 0.0
        return lerp(dense[i - 1], dense[i], t)

    slow = SLOW_SPEED * key_width
    fast = FAST_SPEED * key_width
    frame_ms = 1000.0 / SAMPLE_HZ
    out = []
    s, t = 0.0, 0.0
    while s < total:
        p = point_at(s)
        d = min(dist(p, v) for v in verts)
        f = min(1.0, max(0.0, (d - 0.4 * key_width) / (0.8 * key_width)))
        out.append((p[0], p[1], t))
        s += slow + (fast - slow) * f
        t += frame_ms
    out.append((dense[-1][0], dense[-1][1], t))
    return out


def jitter(points, sigma, rng):
    return [(x + rng.gauss(0, sigma), y + rng.gauss(0, sigma), t) for x, y, t in points]


def presample(points, min_dist):
    """Java GestureStrokeRecognitionPoints: a move point is recorded only when it is
    strictly more than keyWidth/6 away from the last recorded point."""
    if not points:
        return []
    kept = [points[0]]
    for p in points[1:]:
        if math.hypot(p[0] - kept[-1][0], p[1] - kept[-1][1]) > min_dist:
            kept.append(p)
    return kept


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def make_swipe(word, rng):
    verts = word_vertices(word)
    if not verts:
        return None
    dense = densify(smooth_path(verts, CUT_RADIUS * KEY_WIDTH))
    points = speed_profile(dense, verts, KEY_WIDTH)
    points = jitter(points, JITTER_SIGMA * (KEY_WIDTH / 2.0), rng)
    points = presample(points, KEY_WIDTH / 6.0)
    xs, ys, times = [], [], []
    for x, y, t in points:
        xs.append(clamp(int(round(x)), 0, KEYBOARD_WIDTH - 1))
        ys.append(clamp(int(round(y)), 0, KEYBOARD_HEIGHT - 1))
        times.append(int(round(t)))
    return {"word": word, "xs": xs, "ys": ys, "times": times}


def ascii_art(case):
    cols, rows_n = 90, 24
    sx = KEYBOARD_WIDTH / cols
    sy = KEYBOARD_HEIGHT / rows_n
    canvas = [[" "] * cols for _ in range(rows_n)]
    for k in KEYS:
        cx = int((k["x"] + k["w"] / 2) / sx)
        cy = int((k["y"] + k["h"] / 2) / sy)
        canvas[cy][cx] = chr(k["code"]).upper()
    pts = list(zip(case["xs"], case["ys"]))
    for i, (x, y) in enumerate(pts):
        cx = clamp(int(x / sx), 0, cols - 1)
        cy = clamp(int(y / sy), 0, rows_n - 1)
        mark = "@" if i in (0, len(pts) - 1) else "*"
        canvas[cy][cx] = mark
    border = "+" + "-" * cols + "+"
    lines = [border] + ["|" + "".join(row) + "|" for row in canvas] + [border]
    return "\n".join(lines)


def smoke_cases():
    return [
        {"word": "hello", "typed": "helli", "xs": [], "ys": [], "times": []},
        {"word": "the", "typed": "the", "xs": [], "ys": [], "times": []},
        {"word": "keyboard", "typed": "keyboard", "xs": [], "ys": [], "times": []},
    ]


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--wordlist", help="file with one word per line (default: built-in list)")
    ap.add_argument("--variants", type=int, default=3, help="swipes per word (default 3)")
    ap.add_argument("--seed", type=int, default=7, help="RNG seed (default 7)")
    ap.add_argument("--words", type=int, default=0,
                    help="cap the number of words taken from the list (0 = all)")
    ap.add_argument("--out", help="output corpus JSON path (default: stdout)")
    ap.add_argument("--single", metavar="WORD",
                    help="debug: print one swipe for WORD as ASCII art and exit")
    ap.add_argument("--smoke", action="store_true",
                    help="emit a typing-mode smoke corpus (helli->hello etc.) instead of swipes")
    args = ap.parse_args()

    if args.single:
        rng = random.Random(f"{args.seed}:{args.single}:0")
        case = make_swipe(args.single, rng)
        if case is None:
            sys.exit(f"cannot swipe '{args.single}': letter without a key")
        print(ascii_art(case))
        print(f"{args.single}: {len(case['xs'])} points, {case['times'][-1]} ms")
        return

    if args.smoke:
        cases = smoke_cases()
    else:
        if args.wordlist:
            with open(args.wordlist) as f:
                words = [w.strip().lower() for w in f if w.strip()]
        else:
            words = list(dict.fromkeys(DEFAULT_WORDS))
        if args.words > 0:
            words = words[:args.words]
        cases = []
        skipped = []
        for word in words:
            for v in range(args.variants):
                rng = random.Random(f"{args.seed}:{word}:{v}")
                case = make_swipe(word, rng)
                if case is None:
                    skipped.append(word)
                    break
                cases.append(case)
        if skipped:
            print(f"skipped (letters without keys): {' '.join(skipped)}", file=sys.stderr)

    corpus = {"geometry": geometry_json(), "cases": cases}
    text = json.dumps(corpus, separators=(",", ":"))
    if args.out:
        with open(args.out, "w") as f:
            f.write(text)
        print(f"{len(cases)} cases -> {args.out}", file=sys.stderr)
    else:
        print(text)


if __name__ == "__main__":
    main()
