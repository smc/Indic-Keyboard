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

/* Minimal host stub of the JNI surface actually used by the LatinIME suggest core.
 * Shadows the real jni.h via -I ordering; no JVM required. */
#ifndef FAKE_HOST_JNI_H
#define FAKE_HOST_JNI_H

#include <cstddef>
#include <cstring>
#include <vector>

typedef int jint;
typedef long long jlong;
typedef float jfloat;
typedef unsigned char jboolean;
typedef int jsize;

#define JNI_FALSE 0
#define JNI_TRUE 1

struct FakeJniArray {
    std::vector<jint> ints;
    std::vector<jfloat> floats;
    std::vector<jboolean> booleans;
};

typedef FakeJniArray *jintArray;
typedef FakeJniArray *jfloatArray;
typedef FakeJniArray *jbooleanArray;
struct _FakeJniObject {};
typedef _FakeJniObject *jstring;
typedef _FakeJniObject *jclass;
typedef _FakeJniObject *jmethodID;
typedef _FakeJniObject *jobject;
typedef _FakeJniObject *jobjectArray;
typedef char jchar;

struct JNIEnv {
    jsize GetArrayLength(FakeJniArray *a) {
        if (!a) return 0;
        if (!a->ints.empty()) return static_cast<jsize>(a->ints.size());
        if (!a->floats.empty()) return static_cast<jsize>(a->floats.size());
        return static_cast<jsize>(a->booleans.size());
    }
    jsize GetArrayLength(jobjectArray) { return 0; }
    void GetIntArrayRegion(FakeJniArray *a, jsize start, jsize len, jint *buf) {
        memcpy(buf, a->ints.data() + start, len * sizeof(jint));
    }
    void GetFloatArrayRegion(FakeJniArray *a, jsize start, jsize len, jfloat *buf) {
        memcpy(buf, a->floats.data() + start, len * sizeof(jfloat));
    }
    void SetIntArrayRegion(FakeJniArray *a, jsize start, jsize len, const jint *buf) {
        if (a->ints.size() < static_cast<size_t>(start + len)) a->ints.resize(start + len);
        memcpy(a->ints.data() + start, buf, len * sizeof(jint));
    }
    void SetFloatArrayRegion(FakeJniArray *a, jsize start, jsize len, const jfloat *buf) {
        if (a->floats.size() < static_cast<size_t>(start + len)) a->floats.resize(start + len);
        memcpy(a->floats.data() + start, buf, len * sizeof(jfloat));
    }
    void SetBooleanArrayRegion(FakeJniArray *a, jsize start, jsize len, const jboolean *buf) {
        if (a->booleans.size() < static_cast<size_t>(start + len)) a->booleans.resize(start + len);
        memcpy(a->booleans.data() + start, buf, len * sizeof(jboolean));
    }
    jobject GetObjectArrayElement(jobjectArray, jsize) { return nullptr; }
    /* FindClass returning nullptr makes LogUtils::logToJava bail out early. */
    jclass FindClass(const char *) { return nullptr; }
    jmethodID GetStaticMethodID(jclass, const char *, const char *) { return nullptr; }
    jstring NewStringUTF(const char *) { return nullptr; }
    jint CallStaticIntMethod(jclass, jmethodID, ...) { return 0; }
    void DeleteLocalRef(jobject) {}
    void DeleteLocalRef(FakeJniArray *) {}
    FakeJniArray *NewIntArray(jsize n) {
        auto *a = new FakeJniArray();
        a->ints.resize(n);
        return a;
    }
    void ExceptionClear() {}
    jsize GetStringLength(jstring) { return 0; }
    jsize GetStringUTFLength(jstring) { return 0; }
    void GetStringRegion(jstring, jsize, jsize, void *) {}
    void GetStringUTFRegion(jstring, jsize, jsize, char *buf) { if (buf) *buf = 0; }
    void GetBooleanArrayRegion(FakeJniArray *a, jsize start, jsize len, jboolean *buf) {
        if (a && !a->booleans.empty()) {
            memcpy(buf, a->booleans.data() + start, len * sizeof(jboolean));
        } else {
            memset(buf, 0, len * sizeof(jboolean));
        }
    }
    const char *GetStringUTFChars(jstring, jboolean *) { return ""; }
    void ReleaseStringUTFChars(jstring, const char *) {}
};

#endif /* FAKE_HOST_JNI_H */
