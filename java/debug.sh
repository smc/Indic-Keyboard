#!/bin/bash
rm build/outputs/apk/IndicKeyboard-debug.apk
# set up gradle wrapper first
# gradle wrapper --gradle-version 3.3
./gradlew --offline assembleDebug
adb install -r build/outputs/apk/IndicKeyboard-debug.apk
