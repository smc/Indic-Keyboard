#!/usr/bin/env bash
# adb-device.sh: choose the Android device a command should run on.
#
#   scripts/adb-device.sh               print the chosen serial on stdout
#
# How it chooses:
#   one device connected          use it
#   several connected             list them (serial, model, AVD name) and ask
#   none connected                list the emulators on this machine, offer to
#                                 create a Pixel 10 one, boot the choice and
#                                 wait for it
#
# Finds the SDK from ANDROID_HOME, ANDROID_SDK_ROOT or the default install
# location. Prompts go to the terminal; only the serial goes to stdout.

set -eu

die() { echo "adb-device: $*" >&2; exit 1; }
say() { echo "$*" >&2; }

# The first candidate that is really an SDK (has adb), preferring one that
# also has the emulator. Stale ANDROID_HOME values are common, so a variable
# pointing at an empty or missing folder is skipped.
find_sdk() {
  local d fallback=""
  for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    d=${d%/}
    [ -n "$d" ] && [ -x "$d/platform-tools/adb" ] || continue
    if [ -x "$d/emulator/emulator" ]; then echo "$d"; return; fi
    [ -n "$fallback" ] || fallback=$d
  done
  echo "$fallback"
}

SDK=$(find_sdk)
if [ -n "${SDK:-}" ] && [ -x "$SDK/platform-tools/adb" ]; then
  ADB_BIN="$SDK/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
  ADB_BIN=$(command -v adb)
else
  die "adb not found. Install the Android SDK platform tools or set ANDROID_HOME."
fi
EMULATOR_BIN=""
if [ -n "${SDK:-}" ] && [ -x "$SDK/emulator/emulator" ]; then EMULATOR_BIN="$SDK/emulator/emulator"; fi
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"

adb() { "$ADB_BIN" "$@"; }

have_tty() { { : </dev/tty; } 2>/dev/null; }

# Reads one line from the terminal after printing $1.
ask() {
  have_tty || die "need to ask which device, but there is no terminal."
  printf '%s' "$1" >&2
  read -r REPLY </dev/tty || exit 1
}

ready_devices() { adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'; }

avd_name_of() { adb -s "$1" emu avd name 2>/dev/null | head -1 | tr -d '\r'; }

describe() {
  local model avd
  model=$(adb -s "$1" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
  avd=$(avd_name_of "$1")
  printf '%-20s %s%s' "$1" "$model" "${avd:+  ($avd)}"
}

line_count() { if [ -z "$1" ]; then echo 0; else printf '%s\n' "$1" | grep -c .; fi; }

# Asks for a number from 1 to $1 until one is given; sets CHOICE.
ask_number() {
  while :; do
    ask "Pick a device [1-$1]: "
    case "$REPLY" in
      '' | *[!0-9]*) ;;
      *) if [ "$REPLY" -ge 1 ] && [ "$REPLY" -le "$1" ]; then CHOICE=$REPLY; return; fi ;;
    esac
    say "Enter a number from 1 to $1."
  done
}

# --- Creating a Pixel 10 emulator -------------------------------------------

host_abi() { case "$(uname -m)" in arm64 | aarch64) echo arm64-v8a ;; *) echo x86_64 ;; esac; }

# Newest installed system image for this machine, preferring Play Store
# images, then Google APIs, then any. Prints its path under the SDK.
pick_system_image() {
  local abi best="" best_rank=-1 dir api tag rank
  abi=$(host_abi)
  [ -d "$SDK/system-images" ] || return 0
  for dir in "$SDK"/system-images/*/*/"$abi"; do
    [ -f "$dir/package.xml" ] || continue
    api=$(basename "$(dirname "$(dirname "$dir")")")
    tag=$(basename "$(dirname "$dir")")
    case "$tag" in
      google_apis_playstore*) rank=3 ;;
      google_apis*) rank=2 ;;
      default*) rank=1 ;;
      *) continue ;;
    esac
    # Rank by API level first, then by tag; android-37.1 sorts above android-37.0.
    rank=$(printf '%s' "${api#android-}" | awk -v r="$rank" -F. '{ printf "%d", ($1 * 100 + $2) * 10 + r }')
    if [ "$rank" -gt "$best_rank" ]; then best_rank=$rank; best=$dir; fi
  done
  echo "$best"
}

create_pixel10() {
  local image name n api tag abi tag_id rel playstore avdmanager cpu_arch
  image=$(pick_system_image)
  [ -n "$image" ] || die "no system image installed for $(host_abi). Install one in Android Studio (Settings > Languages & Frameworks > Android SDK > SDK Platforms, Show Package Details), then try again."
  abi=$(basename "$image")
  tag=$(basename "$(dirname "$image")")
  api=$(basename "$(dirname "$(dirname "$image")")")

  name=Pixel_10; n=2
  while [ -e "$AVD_HOME/$name.ini" ]; do name="Pixel_10_$n"; n=$((n + 1)); done

  say "Creating $name ($api, $tag, $abi)..."
  avdmanager=""
  if [ -x "$SDK/cmdline-tools/latest/bin/avdmanager" ]; then avdmanager="$SDK/cmdline-tools/latest/bin/avdmanager";
  elif command -v avdmanager >/dev/null 2>&1; then avdmanager=$(command -v avdmanager); fi

  if [ -n "$avdmanager" ]; then
    echo no | "$avdmanager" -s create avd -n "$name" -d pixel_10 \
      -k "system-images;$api;$tag;$abi" >&2 || die "avdmanager could not create $name."
    # A roomier data partition than the default, so installs keep working.
    sed -i.bak 's/^disk.dataPartition.size=.*/disk.dataPartition.size=16G/' "$AVD_HOME/$name.avd/config.ini"
    rm -f "$AVD_HOME/$name.avd/config.ini.bak"
    grep -q '^disk.dataPartition.size=' "$AVD_HOME/$name.avd/config.ini" ||
      echo 'disk.dataPartition.size=16G' >>"$AVD_HOME/$name.avd/config.ini"
  else
    # No command-line tools: write the same files Android Studio would.
    tag_id=$(awk '/<tag>/ { t = 1 } t && /<id>/ { gsub(/.*<id>|<\/id>.*/, ""); print; exit }' "$image/package.xml")
    [ -n "$tag_id" ] || tag_id=$tag
    case "$tag_id" in *playstore*) playstore=true ;; *) playstore=false ;; esac
    rel=${image#"$SDK"/}
    cpu_arch=x86_64
    case "$abi" in arm64*) cpu_arch=arm64 ;; esac
    mkdir -p "$AVD_HOME/$name.avd"
    cat >"$AVD_HOME/$name.ini" <<EOF
avd.ini.encoding=UTF-8
path=$AVD_HOME/$name.avd
path.rel=avd/$name.avd
target=$api
EOF
    cat >"$AVD_HOME/$name.avd/config.ini" <<EOF
AvdId=$name
PlayStore.enabled=$playstore
abi.type=$abi
avd.ini.displayname=Pixel 10
avd.ini.encoding=UTF-8
disk.dataPartition.size=16G
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
hw.accelerometer=yes
hw.audioInput=yes
hw.battery=yes
hw.camera.back=virtualscene
hw.camera.front=emulated
hw.cpu.arch=$cpu_arch
hw.cpu.ncore=4
hw.device.manufacturer=Google
hw.device.name=pixel_10
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.gyroscope=yes
hw.initialOrientation=portrait
hw.keyboard=yes
hw.lcd.density=420
hw.lcd.height=2424
hw.lcd.width=1080
hw.mainKeys=no
hw.ramSize=2048
hw.sdCard=yes
hw.sensors.orientation=yes
hw.sensors.proximity=yes
image.sysdir.1=$rel/
sdcard.size=512M
showDeviceFrame=yes
skin.dynamic=yes
skin.name=pixel_10
skin.path=$SDK/skins/pixel_10
tag.id=$tag_id
target=$api
vm.heapSize=256
EOF
    [ -d "$SDK/skins/pixel_10" ] || sed -i.bak '/^skin\./d;/^showDeviceFrame=/d' "$AVD_HOME/$name.avd/config.ini"
    rm -f "$AVD_HOME/$name.avd/config.ini.bak"
  fi
  say "Created $name."
  CREATED=$name
}

# --- Booting an emulator ----------------------------------------------------

# Boots AVD $1 (unless already starting), waits until Android has finished
# booting, and prints its serial.
boot_avd() {
  local name=$1 serial="" s waited=0 log
  [ -n "$EMULATOR_BIN" ] || die "emulator not found in the SDK."
  for s in $(adb devices | awk 'NR > 1 && $1 ~ /^emulator-/ { print $1 }'); do
    if [ "$(avd_name_of "$s")" = "$name" ]; then serial=$s; fi
  done
  if [ -z "$serial" ]; then
    log="${TMPDIR:-/tmp}/adb-device-$name.log"
    say "Starting $name (log: $log)..."
    nohup "$EMULATOR_BIN" -avd "$name" >"$log" 2>&1 &
  fi
  printf 'Waiting for %s to boot' "$name" >&2
  while [ "$waited" -lt 300 ]; do
    if [ -z "$serial" ]; then
      for s in $(adb devices | awk 'NR > 1 && $1 ~ /^emulator-/ { print $1 }'); do
        if [ "$(avd_name_of "$s")" = "$name" ]; then serial=$s; fi
      done
    fi
    if [ -n "$serial" ] &&
      [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      say " ready."
      echo "$serial"
      return
    fi
    printf '.' >&2
    sleep 3
    waited=$((waited + 3))
  done
  say ""
  die "$name did not finish booting in 5 minutes. See ${TMPDIR:-/tmp}/adb-device-$name.log."
}

# --- Choosing ---------------------------------------------------------------

choose_without_devices() {
  local avds count i choice
  avds=""
  if [ -n "$EMULATOR_BIN" ]; then avds=$("$EMULATOR_BIN" -list-avds 2>/dev/null | grep -v '^INFO' || true); fi
  count=$(line_count "$avds")
  say "No device connected."
  if [ "$count" -gt 0 ]; then
    say "Emulators on this machine:"
    i=0
    for a in $avds; do i=$((i + 1)); say "  $i) $a"; done
  else
    say "No emulators on this machine yet."
  fi
  say "  c) Create a Pixel 10 emulator"
  say "  q) Quit"
  while :; do
    if [ "$count" -gt 0 ]; then ask "Choose [1-$count, c, q]: "; else ask "Choose [c, q]: "; fi
    choice=$REPLY
    case "$choice" in
      q | Q) exit 1 ;;
      c | C) create_pixel10; boot_avd "$CREATED"; return ;;
      '' | *[!0-9]*) ;;
      *) if [ "$choice" -ge 1 ] && [ "$choice" -le "$count" ]; then
           boot_avd "$(printf '%s\n' "$avds" | sed -n "${choice}p")"; return
         fi ;;
    esac
    say "Enter one of the listed choices."
  done
}

choose() {
  local devices count i s
  devices=$(ready_devices)
  count=$(line_count "$devices")
  if [ "$count" -eq 0 ]; then
    choose_without_devices
  elif [ "$count" -eq 1 ]; then
    echo "$devices"
  else
    say "Connected devices:"
    i=0
    for s in $devices; do i=$((i + 1)); say "  $i) $(describe "$s")"; done
    ask_number "$count"
    printf '%s\n' "$devices" | sed -n "${CHOICE}p"
  fi
}

serial=$(choose)
[ -n "$serial" ] || exit 1
say "Using device $serial"

if [ "${1:-}" = "--" ]; then
  shift
  [ "$#" -gt 0 ] || die "nothing to run after --"
  export ANDROID_SERIAL="$serial"
  exec "$@"
fi
echo "$serial"
