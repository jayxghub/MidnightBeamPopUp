# MidnightBeam Quick Popup (Fire TV)

An experimental, unofficial addon to [MidnightBeam](https://github.com/MadeinTaly/midnightbeam), adding **D-pad-friendly floating filter controls** and **saveable/renameable/deletable named presets**. It is designed to be opened through [Stickman](https://github.com/MohamedNofal/stickman) while streaming.

**Status:** The GitHub Actions build passed on 21 November 2026. Actual operation on a Fire TV Stick has **not yet been device-tested**.

## Download APK

Open [GitHub Actions](https://github.com/jayxghub/MidnightBeamPopUp/actions/workflows/build-apk.yml), choose the latest green build, then open the `MidnightBeam-Quick-Popup-APK` artifact at the bottom of the run page. Extract the ZIP to get `midnightbeam.apk`.

## Installation precautions

- Android 8+ is supported by upstream. This fork contains no native libraries, so it does not require ARM64.
- The fork uses package `dev.midnightbeam`, **but has a different signing certificate** from the official app. You **cannot install it as an update**. Installing over the official app returns `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- Save any important settings/presets separately before uninstalling your official version. Uninstalling will normally erase its local app data. Keep the original APK available to reinstall if required.
- Following installation, use atvTools Shell on the Fire Stick to grant overlay permission:

```sh
appops set dev.midnightbeam SYSTEM_ALERT_WINDOW allow
```

- Open **MidnightBeam Quick**, grant necessary app permissions, and assign that launcher to a Stickman long-press. Use D-pad and OK; Back closes the popup. The main MidnightBeam filter still works.

## Build

The workflow clones upstream and applies the addon with `apply_to_upstream.py`, then builds and signs a debug APK using upstream's `build.sh` on GitHub's Linux runner. A future build without the same signing key **cannot update an installed fork**. For repeatable signing, configure a private `QUICK_SIGNING_KEY_B64` GitHub Actions secret using an Android keystore (alias `key`, store and key passwords `android`). Never commit the keystore or secret to this public repository.

## Limitations

- This is a prototype, not affiliated with the original developer.
- Presets are stored in separate SharedPreferences within the fork. They are not synchronized with MidnightBeam phone-web scenes.
- Certain streaming apps or Fire OS versions may interrupt playback when the shortcut launches or may restrict focus in overlay windows; testing is required.
- Future upstream changes may require adjustments to the patch script or code.
