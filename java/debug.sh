#!/bin/bash
rm build/outputs/apk/IndicKeyboard-debug.apk
gradle --offline assembleDebug
adb install -r build/outputs/apk/IndicKeyboard-debug.apk
