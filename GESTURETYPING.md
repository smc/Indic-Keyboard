# Indic Keyboard Gesture Typing

Gesture (swipe/glide) typing decodes a continuous touch path over the keyboard into dictionary
words. The AOSP engine this keyboard is built on ships everything around the decoder -
touch capture, path sampling, a beam-search traversal engine, but the decoder itself was
proprietary and absent. This implementation supplies that decoder: an HMM-style alignment
model that runs inside the existing traversal engine, plus a transliteration layer so
swiping works on Indic transliteration layouts.

## Architecture

```
 touch events
      │
      ▼
 gesture recognition          speed/distance thresholds decide "this is a swipe,
      │                       not taps"; the path accumulates as (x, y, t) points
      ▼
 batch pipeline               path chunks stream to a worker thread while the finger
      │                       moves; the final chunk arrives on finger-up
      ▼
 dictionary selection         normal layouts: the language's main dictionary
      │                       transliteration layouts: a romanized gesture dictionary
      ▼
 ┌────────────────────────── native decoder ──────────────────────────┐
 │ path sampling        resample the raw path; for every sample,      │
 │                      a probability for each nearby key             │
 │ trie traversal       walk the dictionary, letter by letter         │
 │ alignment (HMM)      each letter aligns to one sample; samples     │
 │                      in between are "transit" and get skip costs   │
 │ beam search          keep only the best partial words per depth    │
 │ scoring              blend path fit with word frequency            │
 └──────────────────────────────┬──────────────────────────────────────┘
                                │ ranked candidate words
      ▼
 live transliteration         on transliteration layouts, candidates are converted
      │                       to the target script before display
      ▼
 floating preview + suggestion strip; finger-up commits the top candidate
```

## Capture and batching

The touch tracker classifies a stream as a gesture when movement speed and distance cross
thresholds; otherwise it stays a tap. Gesture points accumulate and are shipped in batches
to a non-UI thread: intermediate batches drive the floating word preview in real time, the
tail batch (finger-up) produces the final candidates and commits the best one. A gesture
that decodes to nothing commits nothing.

## Decoding

### Path sampling

The raw path is resampled into evenly spaced points, keeping timing and speed. For every
sample the engine precomputes a probability distribution over nearby keys — "how likely was
this point aimed at key k" — plus one extra bucket: the probability that the point was not
aimed at any key (pure transit motion). All distances are normalized to key radius, which
makes the model independent of layout, screen density, and language.

### Alignment model

Decoding is framed as an alignment problem, structured like an HMM. A candidate word is a
path through the dictionary trie; each of its letters must claim exactly one sample, in
order; every sample between two claimed samples is transit. Costs are negative log
probabilities from the sampling stage:

- **Align cost** — how poorly the claimed sample fits the letter's key.
- **Skip cost** — how key-like the transit samples between two alignments look. Passing
  directly over an unrelated key is cheap to skip; lingering on it is expensive.

The cost of a word is the sum over its letters. Because costs accumulate incrementally
during trie traversal, partial words compete fairly and hopeless prefixes die early.

### Beam search with alignment alternatives

Greedy alignment — always take the best sample for each letter — breaks on direction
reversals and overshoot, where the locally best sample commits the decode to the wrong
branch. Instead, each matched letter may fork into several alignment candidates (the top
few samples for that key), and a fixed-width beam keeps the best partial hypotheses per
trie depth. The beam explores the alternatives; bad forks are pruned by their accumulated
cost.

### Word endings

- A gesture must trace the whole word: word completions are rejected, and a candidate that
  ends before the path does pays the skip cost of the remaining tail.
- The one exception is a word-final double letter (feel, കൊള്ളാം): its second letter cannot
  claim a distinct sample, so it is admitted at a small fixed cost and flagged so ranking
  does not classify it as a correction.

### Scoring

The final score blends geometry with the language model: path fit (accumulated cost) plus a
weighted unigram/bigram probability. The language-model weight settles what geometry cannot
— ties between words with identical start and end keys, and short same-row words. All
tunables (alignment weight, LM weight, beam width, fork count, double-letter cost) live in
one parameter table.

## Transliteration layouts

The decoder matches geometry against the code points of the loaded dictionary, so a swipe
over Latin keys can never match an Indic-script dictionary. Two mechanisms bridge this:

1. **Romanized gesture dictionary.** The dictionary pipeline reverse-transliterates each
   language's word list into Latin script and compiles it into a separate gesture
   dictionary that ships in the language pack. Letter case is preserved (veeT → വീട്)
   because transliteration schemes are case-significant; the decoder matches keys
   case-insensitively but outputs the stored form. When a Latin-keyed transliteration
   layout is active, gesture decoding is redirected to this dictionary — typing and
   next-word prediction keep using the main one.
2. **Live transliteration.** Decoded Latin candidates are converted to the target script
   before anything is shown: through the varnam translit engine or through the
   layout's own rule-based transliterator.

## Tuning

A host-side harness compiles the same decoder sources for the development machine and
replays swipe corpora against real dictionaries, reporting top-1/top-3 accuracy at around a
millisecond per swipe. Corpora are synthesized from dictionary words (straight segments,
noise, overshoot) or recorded on a device. All parameter tuning happens here — accuracy
must never be judged from adb-injected gestures, whose artificial timing distorts
recognition. Current corpus accuracy: 87.8 % top-1, 96.9 % top-3 over 1305 synthetic
swipes.

Because the model is geometry-over-a-trie with key-radius normalization, the same decoder
and the same parameters serve every language: English qwerty, native-script Indic layouts,
and romanized transliteration layouts differ only in which dictionary is loaded.

