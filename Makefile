# Makefile for building, installing and running Indic Keyboard.
#
# Usage:
#   make build          - assemble the debug APK
#   make install        - build and install the debug APK on $(DEVICE)
#   make run            - install and launch the app
#   make release        - assemble the release APK (needs INDIC_KEYSTORE* env vars)
#   make release-install- install the release APK
#   make uninstall      - remove the app from the device
#   make clear-data     - clear the app's data on the device
#   make clean          - gradle clean
#   make logcat         - stream logcat filtered to the app's process
#   make build-native   - rebuild libjni_latinime.so with the NDK and copy it into jniLibs
#   make build-native-x86 - same, for the x86/x86_64 ABIs (not shipped; manual only)
#
# Overridable variables, e.g.:
#   make build ABI=armeabi-v7a
#   make install DEVICE=adb-XXXX._adb-tls-connect._tcp
#   make build-native NDK_VERSION=30.0.12345678

# ':=' (not '?=') on purpose: shell environments often carry stale
# JAVA_HOME/NDK_HOME values; 'make VAR=value' still overrides these.
ANDROID_SDK := $(HOME)/Library/Android/sdk
JAVA_HOME   := /Applications/Android Studio.app/Contents/jbr/Contents/Home
NDK_VERSION := 29.0.14206865
NDK_HOME    := $(ANDROID_SDK)/ndk/$(NDK_VERSION)
DEVICE      ?= emulator-5554
ABI         ?= arm64-v8a
AVD         ?= Pixel_10_Pro_XL

PKG         := org.smc.inputmethod.indic
ADB         := $(ANDROID_SDK)/platform-tools/adb -s $(DEVICE)
GRADLEW     := JAVA_HOME="$(JAVA_HOME)" ./gradlew
DEBUG_APK   := java/build/outputs/apk/debug/IndicKeyboard-$(ABI)-debug.apk
RELEASE_APK := java/build/outputs/apk/release/IndicKeyboard-$(ABI)-release.apk

.PHONY: build install run release release-install uninstall clear-data clean logcat build-native build-native-x86 device-check

build:
	cd java && $(GRADLEW) assembleDebug

install: build device-check
	$(ADB) install -r $(DEBUG_APK)

run: install
	$(ADB) shell monkey -p $(PKG) -c android.intent.category.LAUNCHER 1

# Signing config comes from the environment:
#   INDIC_KEYSTORE, INDIC_KEYSTORE_PASSWORD, INDIC_KEY_ALIAS, INDIC_KEY_PASSWORD
release:
	@test -n "$$INDIC_KEYSTORE" -a -f "$$INDIC_KEYSTORE" || { \
		echo "Release signing not configured."; \
		echo "Export INDIC_KEYSTORE (path to keystore), INDIC_KEYSTORE_PASSWORD,"; \
		echo "INDIC_KEY_ALIAS and INDIC_KEY_PASSWORD, then re-run 'make release'."; \
		exit 1; }
	cd java && $(GRADLEW) assembleRelease

release-install: release device-check
	$(ADB) install -r $(RELEASE_APK)

uninstall: device-check
	$(ADB) uninstall $(PKG)

clear-data: device-check
	$(ADB) shell pm clear $(PKG)

clean:
	cd java && $(GRADLEW) clean

logcat: device-check
	@pid=$$($(ADB) shell pidof -s $(PKG) | tr -d '\r'); \
	test -n "$$pid" || { echo "$(PKG) is not running. Launch it first: make run"; exit 1; }; \
	echo "Streaming logcat for $(PKG) (pid $$pid). Ctrl-C to stop."; \
	$(ADB) logcat --pid=$$pid

# Cross-compiles for any ABI; ABI takes a space-separated list or 'all', e.g.:
#   make build-native ABI="armeabi-v7a arm64-v8a"
#   make build-native ABI=all
build-native: ABI_LIST = $(if $(filter all,$(ABI)),armeabi-v7a arm64-v8a,$(ABI))
build-native:
	@test -x "$(NDK_HOME)/ndk-build" || { \
		echo "NDK not found at $(NDK_HOME)."; \
		echo "Download it from https://github.com/android/ndk/wiki and pass NDK_HOME=<path>."; \
		exit 1; }
	cd native/jni && "$(NDK_HOME)/ndk-build" -e "APP_ABI=$(ABI_LIST)" -C ./
	@for abi in $(ABI_LIST); do \
		mkdir -p java/jniLibs/$$abi; \
		cp -v native/libs/$$abi/libjni_latinime.so java/jniLibs/$$abi/libjni_latinime.so; \
	done

# x86/x86_64 are not shipped (not in the splits block of java/build.gradle),
# so they are only built when triggered manually.
build-native-x86:
	$(MAKE) build-native ABI="x86 x86_64"

device-check:
	@$(ADB) get-state >/dev/null 2>&1 || { \
		echo "Device '$(DEVICE)' not connected."; \
		echo "Start the emulator with: $(ANDROID_SDK)/emulator/emulator -avd $(AVD)"; \
		echo "Or pass DEVICE=<serial> (see 'adb devices')."; \
		exit 1; }
