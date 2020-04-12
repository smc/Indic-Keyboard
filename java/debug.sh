#!/bin/bash
rm build/outputs/apk/debug/IndicKeyboard-arm64-v8a-debug.apk
# set up gradle wrapper first
# gradle wrapper --gradle-version 5.4.1
./gradlew --offline assembleDebug
adb install -r build/outputs/apk/debug/IndicKeyboard-arm64-v8a-debug.apk
sleep 5
adb logcat | grep `adb shell ps | grep org.smc.inputmethod.indic | awk '{ print $2; }'`
