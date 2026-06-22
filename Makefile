# Makefile for building, installing and running Indic Keyboard.
#
# Run `make` (or `make help`) to list the available commands.
#
# Machine-specific config and release signing live in .env (gitignored), the single
# place to configure toolchain paths, the NDK version, the emulator AVD and the signing
# credentials. Copy .env.sample to .env and edit it there. A command-line override still
# wins, e.g.:
#   make build ABI=armeabi-v7a
#   make install DEVICE=adb-XXXX._adb-tls-connect._tcp
#   make build-native NDK_VERSION=30.0.12345678
ifeq (,$(wildcard .env))
$(error No .env found — copy the template and edit it:  cp .env.sample .env)
endif
include .env
export

NDK_HOME := $(ANDROID_SDK)/ndk/$(NDK_VERSION)
# DEVICE is optional: when unset, adb targets the first connected physical device
# (emulators are skipped — use emulator-install/emulator-run for those).
# ABI is the on-the-fly build target, not machine config, so it defaults here.
DEVICE   ?=
ABI      ?= arm64-v8a

PKG         := org.smc.inputmethod.indic
ADB_BASE    := $(ANDROID_SDK)/platform-tools/adb
PHYS_DEVICE  = $(shell $(ADB_BASE) devices | awk -F'\t' 'NR>1 && $$2=="device" && $$1 !~ /^emulator-/ {print $$1; exit}')
ADB          = $(ADB_BASE) -s "$(or $(DEVICE),$(PHYS_DEVICE))"
GRADLEW     := JAVA_HOME="$(JAVA_HOME)" ./gradlew
DEBUG_APK   := java/build/outputs/apk/debug/IndicKeyboard-$(ABI)-debug.apk
RELEASE_APK := java/build/outputs/apk/release/IndicKeyboard-$(ABI)-release.apk

TEST_APP_DIR := input-test-app
TEST_APP_PKG := org.smc.inputtest
TEST_APP_APK := $(TEST_APP_DIR)/build/outputs/apk/debug/input-test-app-debug.apk
TEST_APP_ACT := $(TEST_APP_PKG)/.MainActivity

.PHONY: help build install run emulator emulator-install emulator-run release release-install rename-release uninstall clear-data clean logcat build-native build-native-x86 keyboard-text dicttool dictionaries dictionaries-en device-check test-app-build test-app-install test-app-emulator-install

.DEFAULT_GOAL := help

help: ## List available commands
	@echo "Indic Keyboard - available make commands:"
	@echo
	@grep -hE '^[a-zA-Z0-9_-]+:.*## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":[^#]*## "} {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'
	@echo
	@echo "Overridable variables: DEVICE=$(if $(DEVICE),$(DEVICE),(auto)) ABI=$(ABI) NDK_VERSION=$(NDK_VERSION)"

build: ## Assemble the debug APK
	cd java && $(GRADLEW) assembleDebug

install: build device-check ## Build and install the debug APK on the device (DEVICE=<serial>)
	$(ADB) install -r $(DEBUG_APK)

run: install ## Install and launch the app
	$(ADB) shell monkey -p $(PKG) -c android.intent.category.LAUNCHER 1

emulator: ## Boot the AVD emulator (if not already running) and wait for it (AVD=<name>)
	@if $(ADB_BASE) -s emulator-5554 get-state >/dev/null 2>&1; then \
		echo "emulator-5554 already running."; \
	else \
		echo "Booting $(AVD)..."; \
		"$(ANDROID_SDK)/emulator/emulator" -avd $(AVD) -no-boot-anim >/dev/null 2>&1 & \
		echo "Waiting for boot..."; \
		$(ADB_BASE) -s emulator-5554 wait-for-device; \
		until [ "$$($(ADB_BASE) -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do \
			sleep 2; \
		done; \
		echo "$(AVD) booted."; \
	fi
	@# AVDs ship with a hardware keyboard, which hides the on-screen keyboard. Force it
	@# to show so this IME is actually visible when a text field is focused.
	@$(ADB_BASE) -s emulator-5554 shell settings put secure show_ime_with_hard_keyboard 1

emulator-install: emulator ## Boot the emulator and install the debug APK on it
	$(MAKE) install DEVICE=emulator-5554

emulator-run: emulator ## Boot the emulator, install and make Indic Keyboard the active IME
	$(MAKE) install DEVICE=emulator-5554
	$(ADB_BASE) -s emulator-5554 shell ime enable $(PKG)/.LatinIME
	$(ADB_BASE) -s emulator-5554 shell ime set $(PKG)/.LatinIME
	@echo "Indic Keyboard is now the active IME. Focus a text field to see it."

test-app-build: ## Assemble the input-type test app's debug APK
	cd $(TEST_APP_DIR) && $(GRADLEW) assembleDebug

test-app-install: test-app-build device-check ## Build, install + launch the input test app on the device
	$(ADB) install -r $(TEST_APP_APK)
	$(ADB) shell am start -n $(TEST_APP_ACT)

test-app-emulator-install: emulator ## Boot the emulator and install + launch the input test app on it
	$(MAKE) test-app-install DEVICE=emulator-5554

# Signing config is read from .env (see the -include near the top):
#   INDIC_KEYSTORE, INDIC_KEYSTORE_PASSWORD, INDIC_KEY_ALIAS, INDIC_KEY_PASSWORD
RELEASE_APK_DIR := java/build/outputs/apk/release
AAPT2            = $(shell ls "$(ANDROID_SDK)/build-tools/"*/aapt2 2>/dev/null | sort | tail -1)
release: ## Assemble the release APK (signing creds in .env)
	@test -n "$$INDIC_KEYSTORE" -a -f "$$INDIC_KEYSTORE" || { \
		echo "Release signing not configured. Set INDIC_KEYSTORE* in .env (see .env.sample)."; \
		exit 1; }
	cd java && $(GRADLEW) assembleRelease
	@echo; echo "Release APKs in $(RELEASE_APK_DIR):"; \
	for f in $(RELEASE_APK_DIR)/*.apk; do \
		[ -f "$$f" ] || continue; \
		ver=$$("$(AAPT2)" dump badging "$$f" 2>/dev/null | \
			sed -n "s/.*versionCode='\([0-9]*\)' versionName='\([^']*\)'.*/v\2 (build \1)/p"); \
		bytes=$$(stat -f%z "$$f"); \
		mb=$$(awk "BEGIN{printf \"%.2f\", $$bytes/1048576}"); \
		printf "  %-45s %8s MB (%s bytes)  %s\n" "$$(basename "$$f")" "$$mb" "$$bytes" "$$ver"; \
	done

release-install: release device-check ## Build and install the release APK
	$(ADB) install -r $(RELEASE_APK)

rename-release: ## Rename built release APKs to <applicationId>_v<version>-<abi>.apk
	@found=; \
	for f in $(RELEASE_APK_DIR)/IndicKeyboard-*-release.apk; do \
		[ -f "$$f" ] || continue; found=1; \
		abi=$$(basename "$$f"); abi=$${abi#IndicKeyboard-}; abi=$${abi%-release.apk}; \
		ver=$$("$(AAPT2)" dump badging "$$f" 2>/dev/null | \
			sed -n "s/.*versionName='\([^']*\)'.*/\1/p"); \
		new="$(RELEASE_APK_DIR)/$(PKG)_v$$ver-$$abi.apk"; \
		mv "$$f" "$$new"; \
		echo "  $$(basename "$$f")  ->  $$(basename "$$new")"; \
	done; \
	[ -n "$$found" ] || { echo "No release APKs in $(RELEASE_APK_DIR). Run 'make release' first."; exit 1; }

uninstall: device-check ## Remove the app from the device
	$(ADB) uninstall $(PKG)

clear-data: device-check ## Clear the app's data on the device
	$(ADB) shell pm clear $(PKG)

clean: ## Clean the gradle build
	cd java && $(GRADLEW) clean

logcat: device-check ## Stream logcat filtered to the app's process
	@pid=$$($(ADB) shell pidof -s $(PKG) | tr -d '\r'); \
	test -n "$$pid" || { echo "$(PKG) is not running. Launch it first: make run"; exit 1; }; \
	echo "Streaming logcat for $(PKG) (pid $$pid). Ctrl-C to stop."; \
	$(ADB) logcat --pid=$$pid

# Cross-compiles for any ABI; ABI takes a space-separated list or 'all', e.g.:
#   make build-native ABI="armeabi-v7a arm64-v8a"
#   make build-native ABI=all
build-native: ABI_LIST = $(if $(filter all,$(ABI)),armeabi-v7a arm64-v8a,$(ABI))
build-native: ## Rebuild libjni_latinime.so with the NDK (ABI=<list>|all) and copy into jniLibs
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
build-native-x86: ## Build the x86/x86_64 native libs (not shipped; manual only)
	$(MAKE) build-native ABI="x86 x86_64"

KBD_TEXT_DIR        := tools/make-keyboard-text
KBD_TEXT_TABLE      := java/src/com/android/inputmethod/keyboard/internal/KeyboardTextsTable.java

DICTTOOL_BUILD := tools/dicttool/build
DICTTOOL_JAR   := $(DICTTOOL_BUILD)/dicttool.jar
JSR305_JAR      = $(shell find $(HOME)/.gradle/caches/modules-2 -name "jsr305-3.0.2.jar" 2>/dev/null | head -1)
DICTTOOL_RUN    = "$(JAVA_HOME)/bin/java" -cp "$(DICTTOOL_JAR):$(JSR305_JAR)" \
		com.android.inputmethod.latin.dicttool.Dicttool

# Test sources (tools/dicttool/tests, the 'test' command) are not built; they
# predate the fork's shortcut removal and no longer compile.
dicttool: ## Build tools/dicttool into tools/dicttool/build/dicttool.jar
	@test -n "$(JSR305_JAR)" || { \
		echo "jsr305 jar not found in the gradle cache. Run 'make build' once first."; exit 1; }
	rm -rf $(DICTTOOL_BUILD)/classes && mkdir -p $(DICTTOOL_BUILD)/classes
	"$(JAVA_HOME)/bin/javac" -nowarn -cp "$(JSR305_JAR)" \
		-d $(DICTTOOL_BUILD)/classes \
		$$(find tools/dicttool/src tools/dicttool/compat \
			java/src/com/android/inputmethod/latin/makedict \
			tests/src/com/android/inputmethod/latin/makedict \
			common/src -name "*.java" \
			-not -path "*/dicttool/Test.java" -not -path "*/compat/android/test/*") \
		java/src/com/android/inputmethod/latin/BinaryDictionary.java \
		java/src/com/android/inputmethod/latin/DicTraverseSession.java \
		java/src/com/android/inputmethod/latin/Dictionary.java \
		java/src/com/android/inputmethod/latin/NgramContext.java \
		java/src/com/android/inputmethod/latin/SuggestedWords.java \
		java/src/org/smc/inputmethod/indic/settings/SettingsValuesForSuggestion.java \
		java/src/com/android/inputmethod/latin/utils/BinaryDictionaryUtils.java \
		java/src/com/android/inputmethod/latin/utils/CombinedFormatUtils.java \
		java/src/com/android/inputmethod/latin/utils/JniUtils.java \
		java/src/com/android/inputmethod/latin/define/DebugFlags.java \
		java/src/com/android/inputmethod/latin/define/DecoderSpecificConstants.java \
		tests/src/com/android/inputmethod/latin/utils/ByteArrayDictBuffer.java
	"$(JAVA_HOME)/bin/jar" cfe $(DICTTOOL_JAR) \
		com.android.inputmethod.latin.dicttool.Dicttool -C $(DICTTOOL_BUILD)/classes .
	@echo "Built $(DICTTOOL_JAR)"

dictionaries: dicttool ## Regenerate java/res/raw/main_<lang>.dict from dictionaries/*_wordlist.combined
	@for f in dictionaries/*_wordlist.combined; do \
		case "$$f" in */emoji_search_wordlist.combined) continue;; esac; \
		lang=$$(basename $$f); lang=$${lang%%_*}; \
		echo "==> java/res/raw/main_$$lang.dict"; \
		$(DICTTOOL_RUN) makedict -s $$f -d java/res/raw/main_$$lang.dict >/dev/null || exit 1; \
	done
	@echo "Done. Note: main_en.dict is NOT regenerated by default; see 'make dictionaries-en'."

emoji: dicttool ## Regenerate emoji-categories.xml + emoji_search.dict from Unicode data (tools/make-emoji)
	python3 tools/make-emoji/make_emoji.py
	@echo "==> java/res/raw/emoji_search.dict"
	$(DICTTOOL_RUN) makedict -s dictionaries/emoji_search_wordlist.combined \
		-d java/res/raw/emoji_search.dict >/dev/null || exit 1
	@echo "Done."

# main_en.dict contains bigram (next-word prediction) data built
# from a richer AOSP-internal wordlist. dictionaries/en_US_wordlist.combined.gz
# has no bigrams, so regenerating from it produces a smaller, less capable
# dictionary. Only run this if you know that is what you want.
dictionaries-en: dicttool ## Regenerate main_en.dict from dictionaries/ (WARNING: loses bigram data)
	gunzip -c dictionaries/en_US_wordlist.combined.gz > $(DICTTOOL_BUILD)/en_US_wordlist.combined
	$(DICTTOOL_RUN) makedict -s $(DICTTOOL_BUILD)/en_US_wordlist.combined \
		-d java/res/raw/main_en.dict

keyboard-text: ## Regenerate KeyboardTextsTable.java from tools/make-keyboard-text/res
	cd $(KBD_TEXT_DIR)/src && \
	"$(JAVA_HOME)/bin/javac" com/android/inputmethod/keyboard/tools/*.java && \
	"$(JAVA_HOME)/bin/jar" cmf ../etc/manifest.txt makekeyboardtext.jar ./ ../res/ && \
	"$(JAVA_HOME)/bin/java" -jar makekeyboardtext.jar -java out && \
	mv out/res/src/com/android/inputmethod/keyboard/internal/KeyboardTextsTable.java \
		../../../$(KBD_TEXT_TABLE) && \
	rm -rf out makekeyboardtext.jar com/android/inputmethod/keyboard/tools/*.class
	@echo "Regenerated $(KBD_TEXT_TABLE)"

device-check:
	@$(ADB) get-state >/dev/null 2>&1 || { \
		if [ -n "$(DEVICE)" ]; then \
			echo "Device '$(DEVICE)' not connected:"; \
		else \
			echo "No physical device connected (emulators are skipped):"; \
		fi; \
		$(ADB_BASE) devices; \
		echo "Connect a physical device, or use 'make emulator-install' / 'make emulator-run'"; \
		echo "to target the emulator. Pass DEVICE=<serial> to pick a specific device."; \
		exit 1; }
