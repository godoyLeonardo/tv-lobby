#!/usr/bin/env bash
# Build, install and make TV Lobby the home screen. `./deploy.sh stock` uninstalls it and gives Home back to Google TV.
set -euo pipefail
cd "$(dirname "$0")"

# The TV's address comes from lobby.properties (see lobby.properties.example); TV= overrides it.
TV="${TV:-$(sed -n 's/^tv=//p' lobby.properties 2>/dev/null)}"
[[ -n "$TV" ]] || { echo "Set tv= in lobby.properties (see lobby.properties.example)." >&2; exit 1; }
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

SIGNING="${TV_LOBBY_SIGNING:-$HOME/.config/tv-lobby/signing.properties}"
[[ -f "$SIGNING" ]] || { echo "No signing key at $SIGNING (see Signing in the README)." >&2; exit 1; }
[[ -f local.properties ]] || echo "sdk.dir=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}" > local.properties
./gradlew -q assembleRelease
# The update closes the lobby; reopen it only if it was on screen (or this is the first setup), so the TV stays on
# whatever is being watched. Without this, a closed lobby falls back to the last app, often the TV's HDMI player.
home_before=$(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
on_screen=$(tv dumpsys window | grep -m1 mCurrentFocus)
adb -s "$TV" install -r app/build/outputs/apk/release/app-release.apk
tv pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS
for p in "${STOCK_HOMES[@]}"; do tv pm disable-user --user 0 "$p" >/dev/null; done
tv cmd package set-home-activity "$PKG/.MainActivity"
if [[ "$home_before" != "$PKG/"* || "$on_screen" == *"$PKG/"* ]]; then
  tv am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
fi
echo "TV Lobby is the home screen."
