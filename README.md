# tv-lobby

A home screen for the living-room TCL Google TV (Android 14). It replaces the Google TV launcher. Everything you use is one or two clicks from Home:

- **Inputs:** Switch 2 (HDMI 2), PS5 (HDMI 3), Apple TV (HDMI 4). Each tile shows whether the device is on, in standby or off (its HDMI-CEC power state when it is on CEC, else the HDMI signal), and wakes the device over HDMI-CEC when you open it. Hold OK (or press Menu) on a tile for **Open** and **Turn off**.
- **Sound:** TV speakers or WiiM Ultra (HDMI eARC). The active output is highlighted. It stays correct when you change the output from the TCL menu.
- **Apps:** pinned apps first, then every other installed TV app. Hold OK (or press Menu) on an app to pin it, move it to the front or hide it; hidden apps wait in a "Hidden apps" tile at the end of the row. These choices are saved on the TV.
- **Goodnight:** after a confirmation, puts every HDMI-CEC device to sleep (consoles, Apple TV, WiiM), then the TV.
- **Settings:** a button next to the clock opens the TV settings.

![TV Lobby](screenshot.png)

## Deploy

ADB over the network must be on (the TV is at `192.168.1.20:5555`; set `TV=` to change it).

```bash
./deploy.sh          # build, install, grant permission, make it Home
./deploy.sh stock    # uninstall TV Lobby, give Home back to Google TV
```

The script does more than install the APK:

- It grants `WRITE_SECURE_SETTINGS`, which the audio switch needs.
- It disables `launcherx` and `setupwraith` for user 0. Their HOME filters have system priorities that always win against a third-party launcher. `stock` turns them back on.

If a firmware update re-enables them or revokes the permission, the app redoes both by itself through the TV's own ADB, at boot and whenever the lobby starts. That is why `./deploy.sh stock` uninstalls the app rather than only re-enabling them.

## Customizing

Edit [`Lobby.kt`](app/src/main/java/com/leo/tvlobby/Lobby.kt):

- `inputs`: tile label, HDMI port, `TvInputService` id, brand mark, gradient colors and an optional wake hint. To list the ids, run `adb shell dumpsys tv_input`. `HW15`–`HW18` are HDMI 1–4.
- `outputs`: TCL's sound-output codes, which are written to the Global setting `tcl_device_out_type`. 2 is the speakers, 16 is ARC/eARC.
- `favoriteApps` / `hiddenApps`: package names; only the defaults until you pin or hide an app from the lobby.

The brand marks in `res/drawable/mark_*.xml` come from [Simple Icons](https://simpleicons.org) (CC0). The trademarks belong to their owners.

The `res/drawable/icon_*.xml` icons are [Material Symbols](https://github.com/google/material-design-icons) (Apache 2.0).

The [Manrope](https://github.com/googlefonts/manrope) font in `assets/fonts` is under the SIL Open Font License (`OFL.txt` next to it).

## How it works

- **HDMI:** `ACTION_VIEW` on `TvContract.buildChannelUriForPassthroughInput(id)`, which TCL's `com.tcl.tv` opens.
- **Wake:** the input tile also runs `cmd hdmi_control deviceselect` for the device on that port (found by port in `dumpsys hdmi_control`), which sends the CEC power-on and makes it the active source. That needs a system permission, so the app runs it through the TV's own network ADB (`127.0.0.1:5555`, via [dadb](https://github.com/mobile-dev-inc/dadb)). The first time, the TV asks to allow debugging for TV Lobby's key: choose **Always allow**. A device that isn't on CEC (Switch 2 here) still switches, it just isn't woken: if its input has no signal, the lobby shows the input's `wakeHint` (for the Switch 2, press HOME on a controller).
- **Turn off:** sends CEC `<Standby>` through `IHdmiControlService.sendStandby`. `service call` can't reach it, because TCL wraps `hdmi_control` in its own binder interface and the shell stamps that interface's token. So the app runs its own `CecTool` class as the shell user with `app_process`, which writes the AOSP token. It reads the transaction number from the framework's own `IHdmiControlService.Stub` at each call, so a firmware update that renumbers the interface doesn't break it.
- **Audio:** TCL's TvSettings switches the output by writing `tcl_device_out_type`, and the TCL audio service reroutes when that value changes. The app writes the same setting.

Requirements: JDK 17+ and the Android SDK (`brew install --cask android-commandlinetools`, platform 35).
