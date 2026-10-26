# tv-lobby

A home screen for the living-room TCL Google TV (Android 14). It replaces the Google TV launcher. Everything you use is one or two clicks from Home:

- **Inputs:** Switch 2 (HDMI 2), PS5 (HDMI 3), Apple TV (HDMI 4). The dot shows the live HDMI state: green means on, amber means standby.
- **Sound:** TV speakers or WiiM Ultra (HDMI eARC). The active output is highlighted. It stays correct when you change the output from the TCL menu.
- **Apps:** favorites first, then every other installed TV app.

![TV Lobby](screenshot.png)

## Deploy

ADB over the network must be on (the TV is at `192.168.1.20:5555`; set `TV=` to change it).

```bash
./deploy.sh          # build, install, grant permission, make it Home
./deploy.sh stock    # give Home back to Google TV
```

The script does more than install the APK:

- It grants `WRITE_SECURE_SETTINGS`, which the audio switch needs.
- It disables `launcherx` and `setupwraith` for user 0. Their HOME filters have system priorities that always win against a third-party launcher. `stock` turns them back on.

## Customizing

Edit [`Lobby.kt`](app/src/main/java/com/leo/tvlobby/Lobby.kt):

- `inputs`: tile label, port and `TvInputService` id. To list the ids, run `adb shell dumpsys tv_input`. `HW15`–`HW18` are HDMI 1–4.
- `outputs`: TCL's sound-output codes, which are written to the Global setting `tcl_device_out_type`. 2 is the speakers, 16 is ARC/eARC.
- `favoriteApps` / `hiddenApps`: package names.

## How it works

- **HDMI:** `ACTION_VIEW` on `TvContract.buildChannelUriForPassthroughInput(id)`, which TCL's `com.tcl.tv` opens.
- **Audio:** TCL's TvSettings switches the output by writing `tcl_device_out_type`, and the TCL audio service reroutes when that value changes. The app writes the same setting.

Requirements: JDK 17+ and the Android SDK (`brew install --cask android-commandlinetools`, platform 35).
