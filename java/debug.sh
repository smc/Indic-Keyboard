#!/bin/bash
rm build/outputs/apk/IndicKeyboard-debug.apk
./gradlew --offline assembleDebug
adb install -r build/outputs/apk/IndicKeyboard-debug.apk
