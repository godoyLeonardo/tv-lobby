#!/usr/bin/env bash
# Build, install and make TV Lobby the home screen. `./deploy.sh stock` uninstalls it and gives Home back to Google TV.
set -euo pipefail
cd "$(dirname "$0")"

TV="${TV:-192.168.1.20:5555}"
PKG=com.leo.tvlobby
# Their HOME filters carry system priorities (2 and 1) that always beat a third-party home, so they get disabled.
STOCK_HOMES=(com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith)

adb connect "$TV" >/dev/null
tv() { adb -s "$TV" shell "$@"; }

if [[ "${1:-}" == "stock" ]]; then
  # Uninstalled, or its boot-time HomeGuard would disable the stock homes again.
  adb -s "$TV" uninstall "$PKG" >/dev/null || true
  for p in "${STOCK_HOMES[@]}"; do tv pm enable "$p" >/dev/null; done
  tv am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
  echo "Home is Google TV again."
  exit 0
fi

[[ -f local.properties ]] || echo "sdk.dir=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}" > local.properties
./gradlew -q assembleDebug
adb -s "$TV" install -r app/build/outputs/apk/debug/app-debug.apk
tv pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS
for p in "${STOCK_HOMES[@]}"; do tv pm disable-user --user 0 "$p" >/dev/null; done
tv cmd package set-home-activity "$PKG/.MainActivity"
tv am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "TV Lobby is the home screen."
