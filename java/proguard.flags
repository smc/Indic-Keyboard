# Keep classes and methods that have the @UsedForTesting annotation
# TODO(b/373579455): Evaluate if <init> needs to be kept.
-keep @com.android.inputmethod.annotations.UsedForTesting class * {
    void <init>();
}
-keepclassmembers class * {
    @com.android.inputmethod.annotations.UsedForTesting *;
}

# Keep classes and methods that have the @ExternallyReferenced annotation
# TODO(b/373579455): Evaluate if <init> needs to be kept.
-keep @com.android.inputmethod.annotations.ExternallyReferenced class * {
    void <init>();
}
-keepclassmembers class * {
    @com.android.inputmethod.annotations.ExternallyReferenced *;
}

# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
# TODO(b/373579455): Evaluate if <init> needs to be kept.
-keep class com.android.inputmethod.latin.AssetFileAddress {
    void <init>();
}
# TODO(b/373579455): Evaluate if <init> needs to be kept.
-keep class com.android.inputmethod.latin.Dictionary {
    void <init>();
}
# TODO(b/373579455): Evaluate if <init> needs to be kept.
-keep class com.android.inputmethod.latin.NgramContext {
    void <init>();
}
# TODO(b/373579455): Evaluate if <init> needs to be kept.
-keep class com.android.inputmethod.latin.makedict.ProbabilityInfo {
    void <init>();
}
# TODO(b/373579455): Evaluate if <init> needs to be kept.
-keep class com.android.inputmethod.latin.utils.LanguageModelParam {
    void <init>();
}

# TODO: remove once used in code.
-keep class com.android.inputmethod.keyboard.KeyboardLayout { *; }

