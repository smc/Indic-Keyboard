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

/* Offline gesture/typing replay tool for the LatinIME suggest core.
 *
 * Reads a JSON corpus of {geometry, cases:[{word, xs, ys, times}]} produced by
 * make_corpus.py (or dumped from a device), rebuilds the native ProximityInfo the
 * way Java ProximityInfo.createNativeProximityInfo does, and drives
 * Dictionary::getSuggestions exactly like the JNI glue
 * (com_android_inputmethod_latin_BinaryDictionary.cpp, getSuggestions).
 *
 * Usage: gesture-replay <corpus.json> [dict-path] [--typing] [--verbose]
 */

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <utility>
#include <vector>

#include "jni.h"

#include "defines.h"
#include "dictionary/property/ngram_context.h"
#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/result/suggestion_results.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/core/suggest_options.h"
#include "suggest/policyimpl/gesture/gesture_suggest_policy_factory.h"
#include "utils/jni_data_utils.h"

using namespace latinime;

namespace {

/* ------------------------------------------------------------------ JSON --- */
/* Minimal recursive-descent parser for the fixed corpus schema. */

struct Json {
    enum Type { Null, Number, String, Array, Object };
    Type type = Null;
    double num = 0.0;
    std::string str;
    std::vector<Json> arr;
    std::vector<std::pair<std::string, Json>> members;

    const Json *find(const char *key) const {
        for (const auto &m : members) {
            if (m.first == key) return &m.second;
        }
        return nullptr;
    }
    int asInt() const { return static_cast<int>(std::llround(num)); }
};

class JsonParser {
 public:
    explicit JsonParser(std::string text) : mText(std::move(text)), mPos(0) {}

    bool parse(Json *out) {
        skipWs();
        if (!parseValue(out)) return false;
        skipWs();
        return mPos == mText.size();
    }

    size_t errorPos() const { return mPos; }

 private:
    void skipWs() {
        while (mPos < mText.size()) {
            const char c = mText[mPos];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') ++mPos;
            else break;
        }
    }

    bool parseValue(Json *out) {
        if (mPos >= mText.size()) return false;
        const char c = mText[mPos];
        if (c == '{') return parseObject(out);
        if (c == '[') return parseArray(out);
        if (c == '"') { out->type = Json::String; return parseString(&out->str); }
        if (matchLiteral("true")) { out->type = Json::Number; out->num = 1; return true; }
        if (matchLiteral("false")) { out->type = Json::Number; out->num = 0; return true; }
        if (matchLiteral("null")) { out->type = Json::Null; return true; }
        return parseNumber(out);
    }

    bool matchLiteral(const char *lit) {
        const size_t n = strlen(lit);
        if (mText.compare(mPos, n, lit) == 0) {
            mPos += n;
            return true;
        }
        return false;
    }

    bool parseNumber(Json *out) {
        const char *start = mText.c_str() + mPos;
        char *end = nullptr;
        const double v = strtod(start, &end);
        if (end == start) return false;
        out->type = Json::Number;
        out->num = v;
        mPos += static_cast<size_t>(end - start);
        return true;
    }

    bool parseString(std::string *out) {
        if (mText[mPos] != '"') return false;
        ++mPos;
        out->clear();
        while (mPos < mText.size()) {
            const char c = mText[mPos++];
            if (c == '"') return true;
            if (c == '\\') {
                if (mPos >= mText.size()) return false;
                const char e = mText[mPos++];
                switch (e) {
                    case '"': out->push_back('"'); break;
                    case '\\': out->push_back('\\'); break;
                    case '/': out->push_back('/'); break;
                    case 'n': out->push_back('\n'); break;
                    case 't': out->push_back('\t'); break;
                    case 'r': out->push_back('\r'); break;
                    case 'b': out->push_back('\b'); break;
                    case 'f': out->push_back('\f'); break;
                    case 'u': {
                        if (mPos + 4 > mText.size()) return false;
                        const int cp = static_cast<int>(
                                strtol(mText.substr(mPos, 4).c_str(), nullptr, 16));
                        mPos += 4;
                        appendUtf8(out, cp);
                        break;
                    }
                    default: return false;
                }
            } else {
                out->push_back(c);
            }
        }
        return false;
    }

    static void appendUtf8(std::string *out, int cp);

    bool parseArray(Json *out) {
        out->type = Json::Array;
        ++mPos;
        skipWs();
        if (mPos < mText.size() && mText[mPos] == ']') { ++mPos; return true; }
        while (true) {
            out->arr.emplace_back();
            if (!parseValue(&out->arr.back())) return false;
            skipWs();
            if (mPos >= mText.size()) return false;
            if (mText[mPos] == ',') { ++mPos; skipWs(); continue; }
            if (mText[mPos] == ']') { ++mPos; return true; }
            return false;
        }
    }

    bool parseObject(Json *out) {
        out->type = Json::Object;
        ++mPos;
        skipWs();
        if (mPos < mText.size() && mText[mPos] == '}') { ++mPos; return true; }
        while (true) {
            std::string key;
            skipWs();
            if (!parseString(&key)) return false;
            skipWs();
            if (mPos >= mText.size() || mText[mPos] != ':') return false;
            ++mPos;
            skipWs();
            out->members.emplace_back(std::move(key), Json());
            if (!parseValue(&out->members.back().second)) return false;
            skipWs();
            if (mPos >= mText.size()) return false;
            if (mText[mPos] == ',') { ++mPos; continue; }
            if (mText[mPos] == '}') { ++mPos; return true; }
            return false;
        }
    }

    std::string mText;
    size_t mPos;
};

void appendUtf8(std::string *out, const int cp) {
    if (cp < 0x80) {
        out->push_back(static_cast<char>(cp));
    } else if (cp < 0x800) {
        out->push_back(static_cast<char>(0xC0 | (cp >> 6)));
        out->push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else if (cp < 0x10000) {
        out->push_back(static_cast<char>(0xE0 | (cp >> 12)));
        out->push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out->push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else {
        out->push_back(static_cast<char>(0xF0 | (cp >> 18)));
        out->push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
        out->push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out->push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    }
}

void JsonParser::appendUtf8(std::string *out, const int cp) { ::appendUtf8(out, cp); }

/* -------------------------------------------------------------- geometry --- */

struct KeyDef {
    int code, x, y, w, h;
};

struct Geometry {
    int width = 0, height = 0, mostCommonKeyWidth = 0, mostCommonKeyHeight = 0;
    std::vector<KeyDef> keys;

    const KeyDef *findKey(const int code) const {
        for (const KeyDef &k : keys) {
            if (k.code == code) return &k;
        }
        return nullptr;
    }

    char nearestKeyLetter(const int x, const int y) const {
        const KeyDef *best = nullptr;
        long bestDist = 0;
        for (const KeyDef &k : keys) {
            const long dx = x - (k.x + k.w / 2), dy = y - (k.y + k.h / 2);
            const long d = dx * dx + dy * dy;
            if (!best || d < bestDist) { best = &k; bestDist = d; }
        }
        return (best && best->code >= ' ' && best->code < 127)
                ? static_cast<char>(best->code) : '?';
    }
};

// config_keyboard_grid_width/height in config-common.xml.
constexpr int kGridWidth = 32;
constexpr int kGridHeight = 16;
// ProximityInfo.java SEARCH_DISTANCE.
constexpr float kSearchDistance = 1.2f;

// Mirrors Java ProximityInfo.computeNearestNeighbors + createNativeProximityInfo:
// a key is a neighbor of a grid cell when the squared distance from the cell center
// to the key's edge is strictly below (int)(mostCommonKeyWidth * 1.2) squared.
std::vector<jint> computeProximityChars(const Geometry &g) {
    const int cellW = (g.width + kGridWidth - 1) / kGridWidth;
    const int cellH = (g.height + kGridHeight - 1) / kGridHeight;
    const int threshold = static_cast<int>(g.mostCommonKeyWidth * kSearchDistance);
    const int thresholdSq = threshold * threshold;
    std::vector<jint> prox(kGridWidth * kGridHeight * MAX_PROXIMITY_CHARS_SIZE,
            NOT_A_CODE_POINT);
    for (int gy = 0; gy < kGridHeight; ++gy) {
        for (int gx = 0; gx < kGridWidth; ++gx) {
            const int cx = gx * cellW + cellW / 2;
            const int cy = gy * cellH + cellH / 2;
            const int base = (gy * kGridWidth + gx) * MAX_PROXIMITY_CHARS_SIZE;
            int n = 0;
            for (const KeyDef &k : g.keys) {
                if (k.code < ' ') continue;
                const int ex = std::min(std::max(cx, k.x), k.x + k.w);
                const int ey = std::min(std::max(cy, k.y), k.y + k.h);
                const int dx = cx - ex, dy = cy - ey;
                if (dx * dx + dy * dy < thresholdSq && n < MAX_PROXIMITY_CHARS_SIZE) {
                    prox[base + n++] = k.code;
                }
            }
        }
    }
    return prox;
}

/* ---------------------------------------------------------------- replay --- */

struct Suggestion {
    std::string word;
    int score;
    int type;
};

std::string asciiLower(const std::string &s) {
    std::string out = s;
    for (char &c : out) {
        if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
    }
    return out;
}

std::vector<int> utf8ToCodePoints(const std::string &s) {
    std::vector<int> out;
    for (size_t i = 0; i < s.size();) {
        const unsigned char c = s[i];
        int cp, len;
        if (c < 0x80) { cp = c; len = 1; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; len = 2; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; len = 3; }
        else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; len = 4; }
        else { ++i; continue; }
        if (i + len > s.size()) break;
        for (int j = 1; j < len; ++j) cp = (cp << 6) | (s[i + j] & 0x3F);
        out.push_back(cp);
        i += len;
    }
    return out;
}

// Mirrors latinime_BinaryDictionary_getSuggestions
// (com_android_inputmethod_latin_BinaryDictionary.cpp:178-261) for one call.
std::vector<Suggestion> runGetSuggestions(JNIEnv *env, const Dictionary &dictionary,
        ProximityInfo *pinfo, DicTraverseSession *session, const std::vector<int> &xs,
        const std::vector<int> &ys, const std::vector<int> &times,
        const std::vector<int> &inputCodePoints, const bool isGesture,
        const std::string &prevWord, const bool prevIsBeginningOfSentence) {
    const int inputSize = static_cast<int>(xs.size());
    std::vector<int> xCoordinates(xs), yCoordinates(ys), timesCopy(times);
    std::vector<int> pointerIds(inputSize, 0);
    // The Java-side buffer is int[MAX_WORD_LENGTH] filled with NOT_A_CODE for gesture.
    std::vector<int> codePoints(std::max<size_t>(inputCodePoints.size(), MAX_WORD_LENGTH),
            NOT_A_CODE_POINT);
    std::copy(inputCodePoints.begin(), inputCodePoints.end(), codePoints.begin());

    const int options[5] = { isGesture ? 1 : 0, 0 /* USE_FULL_EDIT_DISTANCE */,
            0 /* BLOCK_OFFENSIVE_WORDS */, 0 /* SPACE_AWARE_GESTURE_ENABLED */,
            1000 /* WEIGHT_FOR_LOCALE_IN_THOUSANDS, = weightForLocale 1.0 */ };
    SuggestOptions suggestOptions(options, 5);
    std::vector<int> prevCodePoints = utf8ToCodePoints(prevWord);
    if (prevCodePoints.size() > MAX_WORD_LENGTH) prevCodePoints.clear();
    const NgramContext ngramContext =
            (prevCodePoints.empty() && !prevIsBeginningOfSentence)
                    ? NgramContext()
                    : NgramContext(prevCodePoints.data(),
                            static_cast<int>(prevCodePoints.size()), prevIsBeginningOfSentence);
    SuggestionResults results(MAX_RESULTS);
    dictionary.getSuggestions(pinfo, session, xCoordinates.data(), yCoordinates.data(),
            timesCopy.data(), pointerIds.data(), codePoints.data(), inputSize, &ngramContext,
            &suggestOptions, NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL, &results);

    FakeJniArray outCount, outCodePoints, outScores, outSpace, outTypes, outConf, outWeight;
    outCount.ints.resize(1);
    outCodePoints.ints.resize(MAX_WORD_LENGTH * MAX_RESULTS);
    outScores.ints.resize(MAX_RESULTS);
    outSpace.ints.resize(MAX_RESULTS);
    outTypes.ints.resize(MAX_RESULTS);
    outConf.ints.resize(1);
    outWeight.floats.resize(1, NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL);
    results.outputSuggestions(env, &outCount, &outCodePoints, &outScores, &outSpace,
            &outTypes, &outConf, &outWeight);

    std::vector<Suggestion> suggestions;
    for (int i = 0; i < outCount.ints[0]; ++i) {
        std::string word;
        for (int j = 0; j < MAX_WORD_LENGTH; ++j) {
            const int cp = outCodePoints.ints[i * MAX_WORD_LENGTH + j];
            if (cp == 0) break;
            appendUtf8(&word, cp);
        }
        suggestions.push_back({ std::move(word), outScores.ints[i], outTypes.ints[i] });
    }
    // outputSuggestions pops its priority queue in ascending-score order.
    std::stable_sort(suggestions.begin(), suggestions.end(),
            [](const Suggestion &a, const Suggestion &b) { return a.score > b.score; });
    return suggestions;
}

bool readIntArray(const Json *arr, std::vector<int> *out) {
    if (!arr || arr->type != Json::Array) return false;
    out->clear();
    out->reserve(arr->arr.size());
    for (const Json &v : arr->arr) {
        if (v.type != Json::Number) return false;
        out->push_back(v.asInt());
    }
    return true;
}

int usage() {
    fprintf(stderr,
            "usage: gesture-replay <corpus.json> [dict-path] [--typing] [--verbose]\n"
            "  corpus.json  {geometry, cases:[{word, xs, ys, times}]} from make_corpus.py\n"
            "  dict-path    binary dictionary (default: java/res/raw/main_en.dict)\n"
            "  --typing     type each case's code points ('typed' field, else the word)\n"
            "               instead of replaying the gesture points\n"
            "  --verbose    per-case sampled point diagnostics\n");
    return 2;
}

}  // namespace

int main(int argc, char **argv) {
    std::string corpusPath, dictPath;
    bool typingMode = false, verbose = false;
    for (int i = 1; i < argc; ++i) {
        const std::string arg = argv[i];
        if (arg == "--typing") typingMode = true;
        else if (arg == "--verbose") verbose = true;
        else if (!arg.empty() && arg[0] == '-') return usage();
        else if (corpusPath.empty()) corpusPath = arg;
        else if (dictPath.empty()) dictPath = arg;
        else return usage();
    }
    if (corpusPath.empty()) return usage();
    if (dictPath.empty()) {
        dictPath = "/Users/jishnu/git/indic-keyboard/java/res/raw/main_en.dict";
    }

    std::ifstream in(corpusPath, std::ios::binary);
    if (!in) {
        fprintf(stderr, "cannot open corpus: %s\n", corpusPath.c_str());
        return 1;
    }
    std::ostringstream buf;
    buf << in.rdbuf();
    JsonParser parser(buf.str());
    Json root;
    if (!parser.parse(&root) || root.type != Json::Object) {
        fprintf(stderr, "corpus JSON parse error near byte %zu\n", parser.errorPos());
        return 1;
    }

    const Json *geo = root.find("geometry");
    const Json *cases = root.find("cases");
    if (!geo || geo->type != Json::Object || !cases || cases->type != Json::Array) {
        fprintf(stderr, "corpus must have 'geometry' object and 'cases' array\n");
        return 1;
    }

    Geometry g;
    {
        const Json *w = geo->find("width"), *h = geo->find("height");
        const Json *kw = geo->find("mostCommonKeyWidth"), *kh = geo->find("mostCommonKeyHeight");
        const Json *keys = geo->find("keys");
        if (!w || !h || !kw || !kh || !keys || keys->type != Json::Array) {
            fprintf(stderr, "bad geometry (need width/height/mostCommonKeyWidth/"
                    "mostCommonKeyHeight/keys)\n");
            return 1;
        }
        g.width = w->asInt();
        g.height = h->asInt();
        g.mostCommonKeyWidth = kw->asInt();
        g.mostCommonKeyHeight = kh->asInt();
        for (const Json &k : keys->arr) {
            const Json *code = k.find("code"), *x = k.find("x"), *y = k.find("y");
            const Json *kw2 = k.find("w"), *kh2 = k.find("h");
            if (!code || !x || !y || !kw2 || !kh2) {
                fprintf(stderr, "bad key entry in geometry\n");
                return 1;
            }
            g.keys.push_back({ code->asInt(), x->asInt(), y->asInt(),
                    kw2->asInt(), kh2->asInt() });
        }
    }

    struct stat st;
    if (stat(dictPath.c_str(), &st) != 0) {
        fprintf(stderr, "dictionary not found: %s\n", dictPath.c_str());
        return 1;
    }
    auto policy = DictionaryStructureWithBufferPolicyFactory::newPolicyForExistingDictFile(
            dictPath.c_str(), 0 /* bufOffset */, static_cast<int>(st.st_size),
            false /* isUpdatable */);
    if (!policy) {
        fprintf(stderr, "failed to load dictionary: %s\n", dictPath.c_str());
        return 1;
    }
    JNIEnv env;
    Dictionary dictionary(&env, std::move(policy));
    // Java DicTraverseSession: large cache for dictionaries >= 256 KB.
    const bool usesLargeCache = st.st_size >= 256 * 1024;

    std::vector<jint> proximityChars = computeProximityChars(g);
    FakeJniArray prox, keyXs, keyYs, keyWs, keyHs, keyCodes;
    prox.ints = proximityChars;
    for (const KeyDef &k : g.keys) {
        keyXs.ints.push_back(k.x);
        keyYs.ints.push_back(k.y);
        keyWs.ints.push_back(k.w);
        keyHs.ints.push_back(k.h);
        keyCodes.ints.push_back(k.code);
    }
    ProximityInfo pinfo(&env, g.width, g.height, kGridWidth, kGridHeight,
            g.mostCommonKeyWidth, g.mostCommonKeyHeight, &prox,
            static_cast<int>(g.keys.size()), &keyXs, &keyYs, &keyWs, &keyHs, &keyCodes,
            nullptr /* sweetSpotCenterXs */, nullptr, nullptr);

    // Calling into Suggest with the unregistered (nullptr) gesture policy would crash
    // on TRAVERSAL->getMaxSpatialDistance(); report empty results instead so the rest
    // of the plumbing can still be exercised until the policy lands.
    const bool gesturePolicyAvailable =
            GestureSuggestPolicyFactory::getGestureSuggestPolicy() != nullptr;
    if (!typingMode && !gesturePolicyAvailable) {
        printf("NOTE: no native gesture policy registered "
                "(GestureSuggestPolicyFactory returns nullptr) — every gesture case will "
                "report zero suggestions.\n\n");
    }

    printf("mode: %s   dict: %s (%lld bytes)   keys: %zu   cases: %zu\n\n",
            typingMode ? "typing" : "gesture", dictPath.c_str(),
            static_cast<long long>(st.st_size), g.keys.size(), cases->arr.size());

    int total = 0, top1 = 0, top3 = 0, empty = 0;
    double reciprocalRankSum = 0.0;
    const int caseCount = static_cast<int>(cases->arr.size());
    for (int ci = 0; ci < caseCount; ++ci) {
        const Json &c = cases->arr[ci];
        const Json *wordJson = c.find("word");
        if (!wordJson || wordJson->type != Json::String) {
            fprintf(stderr, "case %d: missing 'word'\n", ci);
            return 1;
        }
        const std::string word = wordJson->str;

        std::vector<int> xs, ys, times, inputCodePoints;
        std::string typed;
        if (typingMode) {
            const Json *typedJson = c.find("typed");
            typed = (typedJson && typedJson->type == Json::String) ? typedJson->str : word;
            const std::string lowered = asciiLower(typed);
            bool ok = true;
            for (size_t i = 0; i < lowered.size(); ++i) {
                const int code = static_cast<unsigned char>(lowered[i]);
                const KeyDef *k = g.findKey(code);
                if (!k) { ok = false; break; }
                xs.push_back(k->x + k->w / 2);
                ys.push_back(k->y + k->h / 2);
                times.push_back(static_cast<int>(i) * 100);
                inputCodePoints.push_back(code);
            }
            if (!ok) {
                printf("[%3d/%3d] %-16s SKIPPED (letter without a key: '%s')\n",
                        ci + 1, caseCount, word.c_str(), typed.c_str());
                continue;
            }
        } else {
            if (!readIntArray(c.find("xs"), &xs) || !readIntArray(c.find("ys"), &ys)
                    || !readIntArray(c.find("times"), &times)
                    || xs.empty() || xs.size() != ys.size() || xs.size() != times.size()) {
                fprintf(stderr, "case %d (%s): bad/missing xs/ys/times\n", ci, word.c_str());
                return 1;
            }
        }

        const Json *prevJson = c.find("prev");
        const std::string prevWord =
                (prevJson && prevJson->type == Json::String) ? prevJson->str : "";
        const Json *bosJson = c.find("bos");
        const bool prevIsBos = bosJson && bosJson->type == Json::Number && bosJson->asInt() != 0;

        std::vector<Suggestion> suggestions;
        if (typingMode || gesturePolicyAvailable) {
            DicTraverseSession session(&env, nullptr /* localeStr */, usesLargeCache);
            suggestions = runGetSuggestions(&env, dictionary, &pinfo, &session,
                    xs, ys, times, inputCodePoints, !typingMode, prevWord, prevIsBos);
        }

        // The engine emits one terminal per surviving alignment, so the same word can occupy
        // several slots; SuggestedWords dedups before display on-device, so mirror that here
        // or ranks understate what the user sees.
        {
            std::vector<Suggestion> deduped;
            for (const Suggestion &s : suggestions) {
                bool seen = false;
                for (const Suggestion &d : deduped) {
                    if (asciiLower(d.word) == asciiLower(s.word)) { seen = true; break; }
                }
                if (!seen) deduped.push_back(s);
            }
            suggestions.swap(deduped);
        }

        ++total;
        int rank = 0;
        const std::string expected = asciiLower(word);
        for (size_t i = 0; i < suggestions.size(); ++i) {
            if (asciiLower(suggestions[i].word) == expected) {
                rank = static_cast<int>(i) + 1;
                break;
            }
        }
        if (rank == 1) ++top1;
        if (rank >= 1 && rank <= 3) ++top3;
        if (rank > 0) reciprocalRankSum += 1.0 / rank;
        if (suggestions.empty()) ++empty;

        std::string topLine;
        for (size_t i = 0; i < suggestions.size() && i < 5; ++i) {
            if (i > 0) topLine += "  ";
            topLine += suggestions[i].word + ":" + std::to_string(suggestions[i].score);
        }
        if (suggestions.empty()) topLine = "(no suggestions)";
        char rankStr[8];
        if (rank > 0) snprintf(rankStr, sizeof(rankStr), "%d", rank);
        else snprintf(rankStr, sizeof(rankStr), "-");
        printf("[%3d/%3d] %-16s rank %-3s %s\n", ci + 1, caseCount, word.c_str(), rankStr,
                topLine.c_str());

        if (verbose) {
            std::string keyTrace;
            for (size_t i = 0; i < xs.size(); ++i) keyTrace += g.nearestKeyLetter(xs[i], ys[i]);
            printf("          points=%zu duration=%dms nearest-keys=%s\n",
                    xs.size(), times.empty() ? 0 : times.back(), keyTrace.c_str());
            if (typingMode) {
                printf("          typed=%s\n", typed.c_str());
            } else {
                printf("          (x,y,t):");
                for (size_t i = 0; i < xs.size(); ++i) {
                    printf(" %d,%d,%d", xs[i], ys[i], times[i]);
                }
                printf("\n");
            }
        }
    }

    printf("\n==== summary ====\n");
    printf("cases           : %d\n", total);
    if (total > 0) {
        printf("top-1 accuracy  : %.1f%% (%d/%d)\n", 100.0 * top1 / total, top1, total);
        printf("top-3 accuracy  : %.1f%% (%d/%d)\n", 100.0 * top3 / total, top3, total);
        printf("mean recip rank : %.3f\n", reciprocalRankSum / total);
        printf("empty results   : %d\n", empty);
    }
    return 0;
}

/* Statics normally provided by utils/jni_data_utils.cpp, which is excluded from the
 * host build (it needs the full JNI object API). */
namespace latinime {
const int JniDataUtils::CODE_POINT_NULL = 0;
const int JniDataUtils::CODE_POINT_REPLACEMENT_CHARACTER = 0xFFFD;
}  // namespace latinime
