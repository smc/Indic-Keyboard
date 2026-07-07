#!/bin/sh
# Copyright 2026, Jishnu Mohan <jishnu7@gmail.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Builds ./gesture-replay from the exact LATIN_IME_CORE_SRC_FILES the device .so
# compiles (parsed out of native/jni/NativeFileList.mk), so new policy files flow in
# automatically. utils/jni_data_utils.cpp is excluded: it needs the full JNI object
# API; its two statics are defined in main.cpp instead.
#
# Objects are cached in obj/ with an mtime check; any header change under
# native/jni/src or fake-jni/ triggers a full rebuild (headers carry the tunable
# policy constants, so per-file dependency tracking is not worth the complexity).

set -eu

HARNESS_DIR=$(cd "$(dirname "$0")" && pwd)
JNI_DIR=$(cd "$HARNESS_DIR/../jni" && pwd)
SRC=$JNI_DIR/src
OBJ=$HARNESS_DIR/obj
BIN=$HARNESS_DIR/gesture-replay

CXX=${CXX:-clang++}
CXXFLAGS="-std=c++17 -O2 -DNDEBUG ${EXTRA_CXXFLAGS:-} -Wno-vla-cxx-extension -I$HARNESS_DIR/fake-jni -I$SRC"

CORE_FILES=$(printf 'include %s/NativeFileList.mk\nlist:\n\t@echo $(LATIN_IME_CORE_SRC_FILES)\n' \
        "$JNI_DIR" | make -s -f - list)
FILES=""
for f in $CORE_FILES; do
    [ "$f" = "utils/jni_data_utils.cpp" ] && continue
    FILES="$FILES $f"
done

HDR_STAMP=$OBJ/.headers-stamp
if [ ! -f "$HDR_STAMP" ] || \
   [ -n "$(find "$SRC" "$HARNESS_DIR/fake-jni" -name '*.h' -newer "$HDR_STAMP" -print -quit)" ]; then
    rm -rf "$OBJ"
    mkdir -p "$OBJ"
    touch "$HDR_STAMP"
fi

compile() {
    mkdir -p "$(dirname "$2")"
    echo "CXX ${1#"$JNI_DIR"/}"
    # shellcheck disable=SC2086  # CXXFLAGS must word-split
    $CXX -c $CXXFLAGS "$1" -o "$2"
}

NCPU=$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 4)
pids=""
n=0
wait_all() {
    for p in $pids; do
        wait "$p"
    done
    pids=""
    n=0
}

OBJS=""
for f in $FILES; do
    src=$SRC/$f
    obj=$OBJ/core/${f%.cpp}.o
    OBJS="$OBJS $obj"
    if [ ! -f "$obj" ] || [ "$src" -nt "$obj" ]; then
        compile "$src" "$obj" &
        pids="$pids $!"
        n=$((n + 1))
        [ "$n" -ge "$NCPU" ] && wait_all
    fi
done
wait_all

MAIN_OBJ=$OBJ/main.o
if [ ! -f "$MAIN_OBJ" ] || [ "$HARNESS_DIR/main.cpp" -nt "$MAIN_OBJ" ]; then
    compile "$HARNESS_DIR/main.cpp" "$MAIN_OBJ"
fi

need_link=0
if [ ! -x "$BIN" ]; then
    need_link=1
elif [ -n "$(find "$OBJ" -name '*.o' -newer "$BIN" -print -quit)" ]; then
    need_link=1
fi
if [ "$need_link" -eq 1 ]; then
    echo "LD  ${BIN#"$HARNESS_DIR"/}"
    # shellcheck disable=SC2086
    $CXX $CXXFLAGS "$MAIN_OBJ" $OBJS -o "$BIN"
fi
echo "ready: $BIN"
