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

"""Merge corpus bigram counts into a dictionary in dicttool 'combined' format.

Refreshes each head word's next-word (ngram) entries from a corpus-derived
bigram counts file, so the suggestion strip predicts content pairs like
"happy -> birthday" instead of only 2014-era function words.

Usage:
    # 1. Export the binary dictionary to combined format:
    java -jar tools/dicttool/build/dicttool.jar makedict -s main_xx.dict -o xx.combined
    # 2. Merge corpus bigrams (counts file: "word1 word2<TAB>count" per line):
    tools/bigrams/merge_bigrams.py xx.combined counts.tsv merged.combined
    # 3. Rebuild the binary:
    java -jar tools/dicttool/build/dicttool.jar makedict -s merged.combined -d main_xx.dict -2

Counts files can come from any corpus. For English, norvig.com/ngrams/count_2w.txt
(Google Web Trillion Word corpus excerpt) works well. For other languages, count
adjacent token pairs over any large text corpus and emit the same TSV shape.

Heads present in the counts file get their ngram list replaced by the corpus
top-K; heads absent from it keep their existing entries. Function-word
successors are demoted (not removed) so that content pairs surface within the
3 suggestion slots while overwhelming pairs like "welcome to" still rank first.
"""

import argparse
import re
import sys
from collections import defaultdict

# Demoting instead of removing keeps genuinely dominant function pairs ("welcome to")
# at the top while letting content pairs ("happy birthday") into the visible strip.
STOPWORD_DEMOTION = 0.25
STOPWORDS = frozenset(
    "the a an to of in on at for with and or but that this these those as by "
    "from is are was were be been it its".split())

# The v2 binary format reconstructs a bigram's probability relative to the TARGET's unigram
# frequency, flooring it there — a successor like "and" (f=214) would always outrank content
# successors regardless of the f written here. Listing such a successor at all is therefore
# only useful when it genuinely is the dominant next word ("welcome to").
HIGH_UNIGRAM_CUTOFF = 205

MAX_NGRAMS_PER_WORD = 8
TOP_F = 200
F_STEP = 8

WORD_RE = re.compile(r"^ word=([^,]+),")


def parse_combined(path):
    """Returns (header_line, entries) where each entry is
    {word, lines (word line + non-ngram attribute lines), ngrams: [(target, f)]}."""
    entries = []
    header = None
    current = None
    pending_ngram = None
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if header is None:
                header = line
                continue
            m = WORD_RE.match(line)
            if m:
                current = {"word": m.group(1), "lines": [line], "ngrams": []}
                entries.append(current)
                pending_ngram = None
            elif line.startswith(" ngram="):
                nm = re.match(r"^ ngram=([^,]+),f=(\d+)", line)
                pending_ngram = (nm.group(1), int(nm.group(2)))
            elif line.startswith("  prev_word[0]="):
                if pending_ngram is not None:
                    current["ngrams"].append(pending_ngram)
                    pending_ngram = None
            else:
                current["lines"].append(line)
    return header, entries


def load_counts(path):
    counts = defaultdict(lambda: defaultdict(int))
    with open(path, encoding="utf-8") as f:
        for line in f:
            try:
                pair, count = line.rstrip("\n").split("\t")
                w1, w2 = pair.split(" ")
            except ValueError:
                continue
            counts[w1.lower()][w2.lower()] += int(count)
    return counts


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("combined_in")
    ap.add_argument("counts_tsv")
    ap.add_argument("combined_out")
    ap.add_argument("--top", type=int, default=6, help="corpus successors per head word")
    args = ap.parse_args()

    header, entries = parse_combined(args.combined_in)
    counts = load_counts(args.counts_tsv)

    # Successor targets must exist in the dictionary; prefer the lowercase entry so
    # predictions render uncapitalized.
    by_lower = {}
    unigram_f = {}
    for e in entries:
        w = e["word"]
        if w.lower() not in by_lower or w.islower():
            by_lower[w.lower()] = w
            fm = re.search(r",f=(\d+)", e["lines"][0])
            unigram_f[w.lower()] = int(fm.group(1)) if fm else 0

    replaced = kept = total_ngrams = 0
    for e in entries:
        head = e["word"].lower()
        successors = counts.get(head)
        if not successors:
            kept += 1
            total_ngrams += len(e["ngrams"])
            continue
        ranked = []
        for w2, c in successors.items():
            target = by_lower.get(w2)
            if target is None or w2 == head or not w2.isalpha():
                continue
            adjusted = c * STOPWORD_DEMOTION if w2 in STOPWORDS else c
            ranked.append((adjusted, target))
        if not ranked:
            kept += 1
            total_ngrams += len(e["ngrams"])
            continue
        ranked.sort(key=lambda t: -t[0])
        merged = []
        for i, (_, target) in enumerate(ranked):
            if len(merged) >= args.top:
                break
            if i > 0 and unigram_f.get(target.lower(), 0) >= HIGH_UNIGRAM_CUTOFF:
                continue
            merged.append(target)
        e["ngrams"] = [(t, max(1, TOP_F - i * F_STEP)) for i, t in enumerate(merged)]
        replaced += 1
        total_ngrams += len(e["ngrams"])

    # dicttool's combined WRITER emits the newer "ngram="/"prev_word[0]=" syntax (parsed
    # above), but its READER only understands the older single-line "bigram=" attribute —
    # so that is what must be emitted for makedict to keep the entries.
    with open(args.combined_out, "w", encoding="utf-8") as f:
        f.write(header + "\n")
        for e in entries:
            for line in e["lines"]:
                f.write(line + "\n")
            for target, freq in e["ngrams"]:
                f.write("  bigram=%s,f=%d\n" % (target, freq))

    print("heads refreshed from corpus: %d, kept as-is: %d, total ngrams: %d"
          % (replaced, kept, total_ngrams), file=sys.stderr)


if __name__ == "__main__":
    main()
