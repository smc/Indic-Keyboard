#!/usr/bin/env python3
# Generate emoji resources from Unicode Emoji data.
#
# Inputs (committed under tools/make-emoji/data/):
#   emoji-test.txt                Unicode emoji-test.txt (groups, order, skin-tone sequences, names)
#   emoogle-emoji-keywords.json   Emoogle curated search keywords, ordered by relevance (primary
#                                 keyword source; MIT, see emoogle-data.LICENSE)
#   emoogle-keyword-most-relevant-emoji.json
#                                 Emoogle keyword -> single best emoji; that pair is pinned to the
#                                 top of the keyword's results
#   annotations_en.xml            CLDR English annotations, supplementary keyword coverage for
#                                 emoji absent from the Emoogle set
#
# Outputs:
#   java/res/values/emoji-categories.xml   active category arrays (with skin-tone variations+minSdk)
#   dictionaries/emoji_search_wordlist.combined   keyword->emoji shortcut wordlist (compiled to a
#                                                  binary .dict by `make emoji`)
#
# Run via `make emoji`.

import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(HERE, "data")

EMOJI_TEST = os.path.join(DATA, "emoji-test.txt")
EMOOGLE = os.path.join(DATA, "emoogle-emoji-keywords.json")
EMOOGLE_TOP = os.path.join(DATA, "emoogle-keyword-most-relevant-emoji.json")
ANNOTATIONS = os.path.join(DATA, "annotations_en.xml")
CATEGORIES_XML = os.path.join(ROOT, "java/res/values/emoji-categories.xml")
WORDLIST = os.path.join(ROOT, "dictionaries/emoji_search_wordlist.combined")

MODIFIERS = ["1F3FB", "1F3FC", "1F3FD", "1F3FE", "1F3FF"]
MODIFIER_SET = set(MODIFIERS)
VS16 = "FE0F"

# Unicode group -> our category array name (active "Unicode-8" palette path).
GROUP_TO_ARRAY = {
    "Smileys & Emotion": "emoji_eight_smileys_emotion",
    "People & Body": "emoji_people_body",
    "Animals & Nature": "emoji_eight_animals_nature",
    "Food & Drink": "emoji_eight_food_drink",
    "Travel & Places": "emoji_eight_travel_places",
    "Activities": "emoji_eight_activity",
    "Objects": "emoji_eight_objects",
    "Symbols": "emoji_eight_symbols",
    "Flags": "emoji_flags",
}
ACTIVE_ARRAYS = list(dict.fromkeys(GROUP_TO_ARRAY.values()))

# Emoji version -> Android minSdk (the API level whose system font added that set). Approximate;
# unsupported glyphs render as tofu, which is acceptable. minSdk 0 = always shown.
def version_to_minsdk(ver):
    v = float(ver)
    if v <= 5.0:
        return 0
    if v <= 11.0:
        return 28
    if v <= 12.1:
        return 29
    if v <= 13.1:
        return 30
    if v <= 14.0:
        return 33
    if v <= 15.1:
        return 34
    if v <= 16.0:
        return 35
    return 36  # 17.0 and newer


def base_key(codepoints):
    """Key for grouping skin-tone variants: drop modifiers and the VS16 presentation selector."""
    return tuple(c for c in codepoints if c not in MODIFIER_SET and c != VS16)


def cps_to_str(codepoints):
    return "".join(chr(int(c, 16)) for c in codepoints)


def parse_emoji_test():
    """Return ordered list of base entries and a map base_key -> {modifier: variant codepoints}."""
    bases = []           # dicts: array, cps, emoji, version, name
    variants = {}        # base_key -> {modifier: cps}
    group = None
    line_re = re.compile(
        r"^([0-9A-Fa-f ]+);\s*(\S+)\s*#\s*(\S+)\s+E(\d+\.\d+)\s+(.*)$")
    with open(EMOJI_TEST, encoding="utf-8") as f:
        for line in f:
            if line.startswith("# group:"):
                group = line.split(":", 1)[1].strip()
                continue
            if not line.strip() or line.startswith("#"):
                continue
            m = line_re.match(line)
            if not m:
                continue
            codes, status, emoji, version, name = m.groups()
            if status != "fully-qualified":
                continue
            if group not in GROUP_TO_ARRAY:
                continue  # Component etc.
            cps = codes.split()
            mods = [c for c in cps if c in MODIFIER_SET]
            if mods:
                # A skin-tone variant. Keep only uniform (all modifiers the same tone).
                if len(set(mods)) != 1:
                    continue
                variants.setdefault(base_key(cps), {})[mods[0]] = cps
            else:
                bases.append({
                    "array": GROUP_TO_ARRAY[group],
                    "cps": cps,
                    "emoji": emoji,
                    "version": version,
                    "name": name.strip(),
                })
    return bases, variants


def parse_annotations():
    """Return {emoji_string: set(keyword tokens)} from CLDR annotations (non-tts lines)."""
    kw = {}
    ann_re = re.compile(r'<annotation cp="([^"]*)"(?:\s+type="([^"]*)")?>([^<]*)</annotation>')
    with open(ANNOTATIONS, encoding="utf-8") as f:
        text = f.read()
    for cp, typ, body in ann_re.findall(text):
        if typ == "tts":
            continue
        toks = set()
        for part in body.split("|"):
            toks |= tokenize(part)
        if toks:
            kw.setdefault(cp, set()).update(toks)
    return kw


def parse_emoogle():
    """Return {emoji_string: [keyword phrases]} from the Emoogle dataset (most relevant first)."""
    with open(EMOOGLE, encoding="utf-8") as f:
        return json.load(f)


def parse_most_relevant():
    """Return {emoji_string: set(keyword tokens)} for which that emoji is Emoogle's single best
    result. Keyed by both the emoji and its no-VS16 form so it matches whichever the palette uses."""
    with open(EMOOGLE_TOP, encoding="utf-8") as f:
        data = json.load(f)
    rev = {}
    for keyword, emoji in data.items():
        toks = tokenize(keyword)
        if not toks:
            continue
        for key in (emoji, emoji.replace(VS16_CHAR, "")):
            rev.setdefault(key, set()).update(toks)
    return rev


def tokenize(s):
    return set(t for t in re.findall(r"[a-z0-9]+", s.lower()) if len(t) >= 2)


def item_spec(base, variants):
    cps = base["cps"]
    label = ",".join(c.lower() for c in cps)
    minsdk = version_to_minsdk(base["version"])
    vlist = variants.get(base_key(cps))
    variation_field = None
    if vlist:
        ordered = [vlist[m] for m in MODIFIERS if m in vlist]
        if len(ordered) >= 2:
            variation_field = ";".join(",".join(c.lower() for c in v) for v in ordered)
    if variation_field is None and minsdk == 0:
        return label
    parts = [label, label, str(minsdk)]
    if variation_field is not None:
        parts.append(variation_field)
    return "|".join(parts)


def read_blocks(path):
    """Return (header, footer, {array_name: full <array> block}) from an emoji-categories.xml."""
    with open(path, encoding="utf-8") as f:
        text = f.read()
    blocks = {}
    for m in re.finditer(r'<array\b[^>]*?name="(emoji_[^"]+)"[^>]*?>.*?</array>', text, re.DOTALL):
        blocks[m.group(1)] = m.group(0)
    first = text.index("<array")
    header = text[:first].rstrip() + "\n"
    footer = "</resources>\n"
    return header, footer, blocks


def gen_array(name, items):
    out = ['    <array', '        name="%s"' % name, '        format="string"', '    >']
    for it in items:
        out.append("        <item>%s</item>" % it)
    out.append("    </array>")
    return "\n".join(out)


def write_categories(bases, variants):
    header, footer, blocks = read_blocks(CATEGORIES_XML)
    items_by_array = {}
    for b in bases:
        items_by_array.setdefault(b["array"], []).append(item_spec(b, variants))
    parts = [header]
    # Preserve every array we don't regenerate (legacy non-eight arrays, emoticons, recents...).
    for name, block in blocks.items():
        if name not in ACTIVE_ARRAYS:
            parts.append("    " + block.strip() + "\n")
    for name in ACTIVE_ARRAYS:
        parts.append(gen_array(name, items_by_array.get(name, [])) + "\n")
    parts.append(footer)
    with open(CATEGORIES_XML, "w", encoding="utf-8") as f:
        f.write("\n".join(p.rstrip("\n") for p in parts) + "\n")
    # The single values/ file now carries per-item minSdk, superseding the API-32 bucket.
    v32 = os.path.join(ROOT, "java/res/values-v32/emoji-categories.xml")
    if os.path.exists(v32):
        os.remove(v32)
    total = sum(len(v) for v in items_by_array.values())
    print("emoji-categories.xml: %d emoji across %d categories" % (total, len(items_by_array)))


VS16_CHAR = chr(0xFE0F)


def emoji_token_relevance(base, emoogle, cldr, most_relevant):
    """token -> shortcut frequency (1..14) for one emoji, merged across keyword sources.

    Ordinary sources score 1..13; 14 is reserved for an Emoogle "most relevant" pairing so that
    emoji is pinned to the top of its keyword's results. The Emoogle keyword list is ordered
    most-relevant-first, so earlier keywords score higher. Unicode name words anchor exact-name
    searches; CLDR annotations fill coverage for emoji the Emoogle set omits. The strongest score
    across sources wins for each token."""
    emoji = base["emoji"]
    no_vs16 = emoji.replace(VS16_CHAR, "")
    rel = {}

    def bump(token, value):
        if len(token) >= 2 and value > rel.get(token, 0):
            rel[token] = value

    # Unicode name words.
    name_words = re.findall(r"[a-z0-9]+", base["name"].lower())
    for i, w in enumerate(name_words):
        bump(w, 13 if len(name_words) == 1 else (12 if i == 0 else 10))

    # Emoogle curated keywords, ordered by relevance.
    for j, phrase in enumerate(emoogle.get(emoji) or emoogle.get(no_vs16) or []):
        value = max(4, 12 - j)
        for t in tokenize(phrase):
            bump(t, value)

    # CLDR annotations (supplementary).
    for t in cldr.get(emoji, set()) | cldr.get(no_vs16, set()):
        bump(t, 6)

    # Emoogle "most relevant" pairing: pin this emoji to the very top for these keywords.
    for t in most_relevant.get(emoji, set()) | most_relevant.get(no_vs16, set()):
        bump(t, 14)

    return rel


def write_wordlist(bases, emoogle, cldr, most_relevant):
    # token -> list of (freq, rank, emoji)
    token_emoji = {}
    seen = set()
    for rank, b in enumerate(bases):
        emoji = b["emoji"]
        if emoji in seen:
            continue
        seen.add(emoji)
        for t, freq in emoji_token_relevance(b, emoogle, cldr, most_relevant).items():
            token_emoji.setdefault(t, []).append((freq, rank, emoji))
    lines = ["dictionary=main:en,locale=en,description=emoji search,date=1,version=1"]
    for token in sorted(token_emoji):
        # Most relevant first; cap shortcuts per keyword.
        entries = sorted(token_emoji[token], key=lambda e: (-e[0], e[1]))[:30]
        lines.append(" word=%s,f=%d,not_a_word=true" % (token, entries[0][0]))
        for freq, rank, emoji in entries:
            lines.append("  shortcut=%s,f=%d" % (emoji, freq))
    os.makedirs(os.path.dirname(WORDLIST), exist_ok=True)
    with open(WORDLIST, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("emoji_search_wordlist.combined: %d keywords" % len(token_emoji))


def main():
    for path in (EMOJI_TEST, EMOOGLE, EMOOGLE_TOP, ANNOTATIONS):
        if not os.path.exists(path):
            sys.exit("Missing input data: %s" % path)
    bases, variants = parse_emoji_test()
    emoogle = parse_emoogle()
    most_relevant = parse_most_relevant()
    cldr = parse_annotations()
    write_categories(bases, variants)
    write_wordlist(bases, emoogle, cldr, most_relevant)


if __name__ == "__main__":
    main()
