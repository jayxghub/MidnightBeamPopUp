# MidnightBeam Quick Controls — source addon (prototype)

**Status: source-code implementation, not a verified or built APK.** The upstream repository could not be cloned into the authoring environment, and there is no Android SDK here. The Java and manifest integration require a real Android build and on-device test. The workflow in this package can build an APK using GitHub Actions if added to a GitHub repository.

Upstream: https://github.com/MadeinTaly/midnightbeam (MIT licence, maintained by MadeinTaly). This addon is not affiliated with its developer.

## What this adds

* A separate **MidnightBeam Quick** launcher entry (intended for assigning to a Stickman remote-button long press)
* A floating, D-pad/OK navigable popup displayed over the current app using Android overlay permission
* Three quick defaults (Evening, Wind Down, Sleep), plus **Save new**, **Update / Rename**, and **Delete**
* Live controls for darkness, warmth, and colour temperature, forwarded to the app's existing `DimService`
* `Back` dismisses the popup while leaving the filter applied; opening the shortcut again toggles the popup closed
* Presets are stored locally in app SharedPreferences; separate from scenes stored in the phone app
* A `Filter off` button that disables MidnightBeam's overlay

## Why it must be integrated into MidnightBeam

The app's existing `DimService` is exported only with `android.permission.DUMP`. A separately installed companion cannot call it normally. This addon lives **inside the same `dev.midnightbeam` app package**, so it can pass settings without weakening that security model.

## Apply the addon

From a directory containing both the upstream checkout and this addon folder:

```bash
git clone --recurse-submodules https://github.com/MadeinTaly/midnightbeam.git
python3 midnightbeam-quickpopup/apply_to_upstream.py midnightbeam
cd midnightbeam
# Use upstream build.sh with Android SDK build tools and a suitable android.jar
BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0" ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar" ./build.sh
```

The workflow `.github/workflows/build-quick.yml` separately clones upstream and applies the addon, so it can be stored in an addon-only repository. After it finishes, retrieve `midnightbeam-quickpopup-apk` from the workflow run's artifacts.

## Fire Stick installation and settings

**VERY IMPORTANT:** the official MidnightBeam APK has a different signing key. Android will report `INSTALL_FAILED_UPDATE_INCOMPATIBLE` if you try to install this fork over the official app. Preserve any settings you can through the existing phone remote, then uninstall the official build **only when you're ready to lose its local settings**. Install the fork using atvTools. Do not uninstall the working app until the custom APK has been successfully built and inspected.

Once installed, re-grant the overlay permission in atvTools Shell:

```sh
appops set dev.midnightbeam SYSTEM_ALERT_WINDOW allow
```

In Stickman, bind a long-press to launch **MidnightBeam Quick**. Use D-pad arrows to focus presets, sliders, name editor and buttons. Press OK to apply. Press Back to close without turning off the filter.

## Caveats

* Has not been compiled or device-tested yet. Fire OS can handle focused overlay windows differently; video apps can pause when the tiny launcher Activity becomes visible.
* New presets live in the TV build's own app storage, not shared to the existing phone-app Scenes section.
* A Fire Stick doesn't need 64-bit native libraries for this addon: it is plain Java and its service is inside the original Android app.
* This is a prototype, not a safe one-click update. Keep the original APK available as a recovery path.
* The upstream auto-updater may still offer official builds even though they cannot install over this separately signed fork. Ignore official update prompts.
* GitHub Actions generates a new debug signing key unless you configure the `QUICK_SIGNING_KEY_B64` repository secret. To preserve presets across future fork versions, create one private `debug.keystore` using upstream's build script once, then store its base64 content as that secret. The upstream `build.sh` expects alias `key` and passwords `android` / `android`. Never publish a private keystore.

## Future improvements

Optional: a true slider bar for black dimming rather than mapping to `bright`, importing/exporting presets to the phone web remote, context-aware names, and a more refined skin consistent with MidnightBeam's Arcade UI.
