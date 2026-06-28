#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <libgovarnam.h>

JNIEXPORT jint JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1init(JNIEnv *env, jobject thiz, jstring vst_file,
                                                    jstring learnings_file) {
    const char* vstFileConst = (*env)->GetStringUTFChars(env, vst_file, JNI_FALSE);
    const char* learningsFileConst = (*env)->GetStringUTFChars(env, learnings_file, JNI_FALSE);

    char* vstFile = strdup(vstFileConst);
    char* learningsFile = strdup(learningsFileConst);

    int handle = 0;
    int status = varnam_init(vstFile, learningsFile, &handle);

    // Get a reference to this object's class
    jclass thisClass = (*env)->GetObjectClass(env, thiz);

    // Get the Field ID of the instance variables "handle"
    jfieldID fidHandle = (*env)->GetFieldID(env, thisClass, "handle", "I");
    if (fidHandle == NULL) return VARNAM_ERROR;
    (*env)->SetIntField(env, thiz, fidHandle, handle);

    return status;
}

JNIEXPORT jstring JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1get_1last_1error(JNIEnv *env, jobject thiz,
                                                                jint handle) {
    jstring err = (*env)->NewStringUTF(env, varnam_get_last_error(handle));
    return err;
}

JNIEXPORT jint JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1close(JNIEnv *env, jobject thiz, jint handle) {
    return varnam_close(handle);
}

JNIEXPORT void JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1set_1dictionary_1suggestions_1limit(JNIEnv *env,
                                                                                   jobject thiz,
                                                                                   jint handle,
                                                                                   jint limit) {
    varnam_set_dictionary_suggestions_limit(handle, limit);
}

JNIEXPORT void JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1set_1tokenizer_1suggestions_1limit(JNIEnv *env,
                                                                                  jobject thiz,
                                                                                  jint handle,
                                                                                  jint limit) {
    varnam_set_tokenizer_suggestions_limit(handle, limit);
}

JNIEXPORT jint JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1learn(JNIEnv *env, jobject thiz, jint handle,
                                                     jstring word) {
    const char* wordConst = (*env)->GetStringUTFChars(env, word, JNI_FALSE);
    char* wordChar = strdup(wordConst);
    int status = varnam_learn(handle, wordChar, 0);
    (*env)->ReleaseStringUTFChars(env, word, wordConst);
    free(wordChar);
    return status;
}

JNIEXPORT jint JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1unlearn(JNIEnv *env, jobject thiz, jint handle,
                                                     jstring word) {
    const char* wordConst = (*env)->GetStringUTFChars(env, word, JNI_FALSE);
    char* wordChar = strdup(wordConst);
    int status = varnam_unlearn(handle, wordChar);
    (*env)->ReleaseStringUTFChars(env, word, wordConst);
    free(wordChar);
    return status;
}

jobjectArray makeJavaSuggestionArray (JNIEnv* env, varray* sugs) {
    jclass jSugClass = (*env)->FindClass(env, "com/varnamproject/govarnam/Suggestion");
    jobjectArray sugArray = (*env)->NewObjectArray(env, varray_length(sugs), jSugClass, NULL);

    jfieldID WordID = (*env)->GetFieldID(env, jSugClass , "Word", "Ljava/lang/String;");
    jfieldID WeightID = (*env)->GetFieldID(env, jSugClass , "Weight", "I");

    jmethodID constructorID = (*env)->GetMethodID(env, jSugClass, "<init>", "()V");

    jobject obj;
    jint ji;
    for (int i = 0; i < varray_length(sugs); i++) {
        ji = i;
        Suggestion* sug = (Suggestion*) varray_get(sugs, i);

        obj = (*env)->NewObject(env, jSugClass, constructorID);
        (*env)->SetObjectField(env, obj, WordID, (*env)->NewStringUTF(env, sug->Word));
        (*env)->SetIntField(env, obj, WeightID, (jint) sug->Weight);
        (*env)->SetObjectArrayElement(env, sugArray, ji, obj);
    }

    return sugArray;
}

jobject JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1transliterate(JNIEnv *env, jobject thiz,
                                                                       jint handle, jint id,
                                                                       jstring word) {
    const char* wordConst = (*env)->GetStringUTFChars(env, word, JNI_FALSE);

    char* wordChar = strdup(wordConst);
//  __android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "Need to print : %s", wordChar);

    varray *result;
    int code = varnam_transliterate(handle, id, wordChar, &result);

    free(wordChar);
    (*env)->ReleaseStringUTFChars(env, word, wordConst);

    if (code != VARNAM_SUCCESS) {
        return NULL;
    }

    return makeJavaSuggestionArray(env, result);
}

jobject JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1transliterate_1advanced(JNIEnv *env, jobject thiz,
                                                                       jint handle, jint id,
                                                                       jstring word) {
    const char* wordConst = (*env)->GetStringUTFChars(env, word, JNI_FALSE);
    char* wordChar = strdup(wordConst);
//  __android_log_print(ANDROID_LOG_DEBUG, "LOG_TAG", "Need to print : %s", wordChar);

    TransliterationResult *result;
    int code = varnam_transliterate_advanced(handle, id, wordChar, &result);

    free(wordChar);
    (*env)->ReleaseStringUTFChars(env, word, wordConst);

    if (code != VARNAM_SUCCESS) {
        return NULL;
    }

    jobjectArray ExactMatches = makeJavaSuggestionArray(env, result->ExactMatches);
    jobjectArray DictionarySuggestions = makeJavaSuggestionArray(env, result->DictionarySuggestions);
    jobjectArray PatternDictionarySuggestions = makeJavaSuggestionArray(env, result->PatternDictionarySuggestions);
    jobjectArray TokenizerSuggestions = makeJavaSuggestionArray(env, result->TokenizerSuggestions);
    jobjectArray GreedyTokenized = makeJavaSuggestionArray(env, result->GreedyTokenized);

    jclass jTRclass = (*env)->FindClass(env, "com/varnamproject/govarnam/TransliterationResult");
    jmethodID constructorID = (*env)->GetMethodID(env, jTRclass, "<init>",
                                        "([Lcom/varnamproject/govarnam/Suggestion;[Lcom/varnamproject/govarnam/Suggestion;[Lcom/varnamproject/govarnam/Suggestion;[Lcom/varnamproject/govarnam/Suggestion;[Lcom/varnamproject/govarnam/Suggestion;)V");

    int size = 5;
    jvalue* args = malloc(size * sizeof(jvalue));
    args[0].l = ExactMatches;
    args[1].l = DictionarySuggestions;
    args[2].l = PatternDictionarySuggestions;
    args[3].l = TokenizerSuggestions;
    args[4].l = GreedyTokenized;

    return (*env)->NewObjectA(env, jTRclass, constructorID, args);
}

JNIEXPORT jint JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1cancel(JNIEnv *env, jobject thiz, jint id) {
    return varnam_cancel(id);
}

JNIEXPORT jobject JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1learn_1from_1file(JNIEnv *env, jobject thiz, jint id, jstring path) {
    const char* filePathConst = (*env)->GetStringUTFChars(env, path, JNI_FALSE);
    char* filePathChar = strdup(filePathConst);

    LearnStatus* learnStatus;
    int code = varnam_learn_from_file(id, filePathChar, &learnStatus);

    free(filePathChar);
    (*env)->ReleaseStringUTFChars(env, path, filePathConst);

    if (code != VARNAM_SUCCESS) {
        return NULL;
    }

    jclass jLearnStatusClass = (*env)->FindClass(env, "com/varnamproject/govarnam/LearnStatus");

    jfieldID TotalWordsID = (*env)->GetFieldID(env, jLearnStatusClass , "TotalWords", "I");
    jfieldID FailedWordsID = (*env)->GetFieldID(env, jLearnStatusClass , "FailedWords", "I");

    jmethodID constructorID = (*env)->GetMethodID(env, jLearnStatusClass, "<init>", "()V");

    jobject obj = (*env)->NewObject(env, jLearnStatusClass, constructorID);
    (*env)->SetIntField(env, obj, TotalWordsID, (jint) learnStatus->TotalWords);
    (*env)->SetIntField(env, obj, FailedWordsID, (jint) learnStatus->FailedWords);

    return obj;
}

JNIEXPORT jint JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1import(JNIEnv *env, jobject thiz, jint id, jstring path) {
    const char* filePathConst = (*env)->GetStringUTFChars(env, path, JNI_FALSE);
    char* filePathChar = strdup(filePathConst);

    int code = varnam_import(id, filePathChar);

    free(filePathChar);
    (*env)->ReleaseStringUTFChars(env, path, filePathConst);

    return code;
}

JNIEXPORT jint JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1export(JNIEnv *env, jobject thiz, jint id, jstring path, jint wordsPerFile) {
    const char* filePathConst = (*env)->GetStringUTFChars(env, path, JNI_FALSE);
    char* filePathChar = strdup(filePathConst);

    int code = varnam_export(id, filePathChar, wordsPerFile);

    free(filePathChar);
    (*env)->ReleaseStringUTFChars(env, path, filePathConst);

    return code;
}

JNIEXPORT jobject JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1get_1recently_1learned_1words(JNIEnv *env, jobject thiz,
                                                             jint handle, jint id, jint offset, jint limit) {
    varray *result;
    int code = varnam_get_recently_learned_words(handle, id, offset, limit, &result);

    if (code != VARNAM_SUCCESS) {
        return NULL;
    }

    return makeJavaSuggestionArray(env, result);
}

JNIEXPORT void JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1set_1vst_1lookup_1dir(JNIEnv *env, jobject thiz, jstring vst_dir) {
    const char* vstDirConst = (*env)->GetStringUTFChars(env, vst_dir, JNI_FALSE);
    char* vstDir = strdup(vstDirConst);
    varnam_set_vst_lookup_dir(vstDir);
}

JNIEXPORT jobjectArray JNICALL
Java_com_varnamproject_govarnam_Varnam_varnam_1get_1all_1scheme_1details(JNIEnv *env, jobject thiz) {
    varray *result = varnam_get_all_scheme_details();

    jclass jSchemeDetailsClass = (*env)->FindClass(env, "com/varnamproject/govarnam/SchemeDetails");
    jobjectArray sdArray = (*env)->NewObjectArray(env, varray_length(result), jSchemeDetailsClass, NULL);

    jfieldID IdentifierID = (*env)->GetFieldID(env, jSchemeDetailsClass , "Identifier", "Ljava/lang/String;");
    jfieldID LangCodeID = (*env)->GetFieldID(env, jSchemeDetailsClass , "LangCode", "Ljava/lang/String;");
    jfieldID DisplayNameID = (*env)->GetFieldID(env, jSchemeDetailsClass , "DisplayName", "Ljava/lang/String;");
    jfieldID AuthorID = (*env)->GetFieldID(env, jSchemeDetailsClass , "Author", "Ljava/lang/String;");
    jfieldID CompiledDateID = (*env)->GetFieldID(env, jSchemeDetailsClass , "CompiledDate", "Ljava/lang/String;");
    jfieldID IsStableID = (*env)->GetFieldID(env, jSchemeDetailsClass , "IsStable", "Z");

    jmethodID constructorID = (*env)->GetMethodID(env, jSchemeDetailsClass, "<init>", "()V");

    jobject obj;
    jint ji;
    for (int i = 0; i < varray_length(result); i++) {
        ji = i;
        SchemeDetails* sd = (SchemeDetails*) varray_get(result, i);

        obj = (*env)->NewObject(env, jSchemeDetailsClass, constructorID);
        (*env)->SetObjectField(env, obj, IdentifierID, (*env)->NewStringUTF(env, sd->Identifier));
        (*env)->SetObjectField(env, obj, LangCodeID, (*env)->NewStringUTF(env, sd->LangCode));
        (*env)->SetObjectField(env, obj, DisplayNameID, (*env)->NewStringUTF(env, sd->DisplayName));
        (*env)->SetObjectField(env, obj, AuthorID, (*env)->NewStringUTF(env, sd->Author));
        (*env)->SetObjectField(env, obj, CompiledDateID, (*env)->NewStringUTF(env, sd->CompiledDate));
        (*env)->SetBooleanField(env, obj, IsStableID, (jboolean) sd->IsStable);
        (*env)->SetObjectArrayElement(env, sdArray, ji, obj);
    }

    return sdArray;
}
