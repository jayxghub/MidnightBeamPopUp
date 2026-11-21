# MidnightBeam Quick — Fire TV / Android TV

An unofficial, remote-friendly floating overlay for [MidnightBeam](https://github.com/MadeinTaly/midnightbeam), developed for an Android 9/ARMv7 Fire TV Stick and controlled through [Stickman](https://github.com/MohamedNofal/stickman).

## Version 3 — warm quick panel (21 November 2026)

- Right-hand amber / espresso floating panel, approximately 30% of the TV's width
- No blue accents, no keyboard and no scrolling
- **Night filter On/Off** switch at the top
- **Darkness**, **Warm overlay** and **Colour temperature** D-pad-adjustable sliders
- Exactly **four presets**: Evening, Wind Down, Sleep, Custom
- **Every slider change automatically saves the selected preset's values**
- Selecting a preset immediately restores that preset's values (when the filter is on)
- Closing the popup leaves the filter running; switching off leaves the saved values intact
- **Open full settings** at the bottom opens the original MidnightBeam interface
- Stickman's **Open an app** action launches the quick panel

The four presets are separate from scenes in the original MidnightBeam phone/web UI. Android retains them across app restarts and reboots but not app uninstallation.

## Get the APK

Visit [GitHub Actions](https://github.com/jayxghub/MidnightBeamPopUp/actions/workflows/build-apk.yml), open the latest **successful** build, and download the **MidnightBeam-Quick-Popup-APK** artifact. Unzip it and install the included midnightbeam.apk via atvTools.

**Important:** GitHub Actions currently generates a new temporary Android signing key each build unless you configure the QUICK_SIGNING_KEY_B64 repository Actions secret. Thus a new build cannot update an earlier custom build: uninstall the old version first, which clears its locally stored settings. For updates without losing presets, configure a persistent private signing key for future builds. Never commit the private signing key into this public repository.

Grant overlay permission again after reinstalling via atvTools Shell:

    appops set dev.midnightbeam SYSTEM_ALERT_WINDOW allow

The only app launcher is now MidnightBeam Quick, which opens the popup. If Stickman still shows the old launcher, restart Stickman. Or launch from atvTools Shell:

    am start -n dev.midnightbeam/.QuickPopupActivity

Use Open full settings from the panel for MidnightBeam's original settings, or start them explicitly:

    am start -n dev.midnightbeam/.MainActivity

## Build and testing

The workflow fetches MidnightBeam upstream and its submodules, copies the addon into it with apply_to_upstream.py, builds it with Android SDK 35, and signs an APK. No ARM64 native library dependency is introduced. Minimum Android is determined by upstream (currently Android 8.0).

Earlier v1/v2 popups and the Stickman launcher have been confirmed to work on a Fire Stick. This v3 redesign has compiled successfully, but the new layout, autosave and toggle behaviour are **not yet verified on the device**.

This unofficial project is independent of the original MidnightBeam developer.