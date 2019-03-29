#!/bin/bash
rm build/outputs/apk/debug/IndicKeyboard-debug.apk
./gradlew --offline assembleDebug
adb install -r build/outputs/apk/debug/IndicKeyboard-debug.apk
