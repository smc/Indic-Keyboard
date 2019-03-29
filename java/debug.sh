#!/bin/bash
rm build/outputs/apk/debug/IndicKeyboard-debug.apk
# set up gradle wrapper first
# gradle wrapper --gradle-version 4.10.1
./gradlew --offline assembleDebug
adb install -r build/outputs/apk/debug/IndicKeyboard-debug.apk
