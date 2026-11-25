# AA Sound – audio mode toggle for OpenDroidAuto on Honda Connect

A tiny widget for the Honda Connect head unit (Fujitsu Ten "Display Audio", Android 4.0.4) that
switches the audio mode of [OpenDroidAuto](https://github.com/hirneagabriel/OpenDroidAuto) with a
single tap. Tested on a 2017 Honda CR-V.

## The problem

The head unit decides per app how its sound is played. For OpenDroidAuto (process `it.smg.hu`)
there are two useful settings, and the firmware offers nothing in between:

| Mode      | Sound Out | Sound Interrupt | Interrupt Mute | Effect                                               |
|-----------|-----------|-----------------|----------------|------------------------------------------------------|
| Exclusive | on        | off             | off            | Stereo on both speakers, the radio is paused         |
| Mix       | on        | on              | off            | Plays alongside the radio, but on the left speaker only |

Changing this normally means opening S_Mike's "Honda Permissions" app and editing the entry by
hand. AA Sound does it with one tap.

## What it does

- **Mix → Exclusive:** remembers the current three sound values, then sets on / off / off.
- **Exclusive → Mix:** restores the remembered values (default: on / on / off).
- Tells the system that the whitelist has changed, so the new setting applies.

The widget shows the current mode by colour and text. Tapping the app icon "AA Sound" toggles as
well, in case the launcher has no room for the widget.

## Download

The ready-to-install APK is in this repository:
[`app/release/HondaAppCenter_A1.apk`](app/release/HondaAppCenter_A1.apk)

The file name must stay `HondaAppCenter_A1.apk`. The head unit installs nothing else from a USB
stick.

## Installation

1. Copy `HondaAppCenter_A1.apk` to a USB stick and install it the same way as OpenDroidAuto.
2. In the Honda Permissions app, add an entry for `com.rucksack.odatoggle`:
   Auth Type `PREINSTALL`, App Type `OTHER`, Video Out `STILL_IMAGE`, all sound checkboxes off.
3. Place the widget on the home screen, or tap the "AA Sound" icon.

Before the first use, write down the current values of `it.smg.hu` in the Permissions app so you
can restore them by hand if needed.

## Languages

English, German, Chinese (Simplified), Hindi, Spanish, French, Arabic, Bengali, Portuguese,
Russian and Urdu. The app follows the language of the head unit and falls back to English.

## How it works

The app reads and writes the head unit's process whitelist through the system class
`com.fujitsu_ten.displayaudio.whitelist.common.IWhiteList`, the same API the Permissions app
uses. That class is already on the head unit. The app only compiles against
`app/libs-compile/ada-ext-api.jar` (taken from OpenDroidAuto) and must not ship its own copy.

## Building

Requirements: JDK 21 and the Android SDK (compileSdk 34).

```
gradlew assembleRelease
```

The result is `app/build/outputs/apk/release/HondaAppCenter_A1.apk`. To sign it, put a
`keystore.properties` file next to `settings.gradle` (it is git-ignored):

```
storeFile=path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Settings that have to stay as they are for Android 4.0.4:

- `minSdk 15`, `targetSdk 15`, Java 1.8, `minifyEnabled false`
- no AndroidX or support libraries
- launcher icon as PNG (an XML drawable crashed the head unit's installer)
- `ada-ext-api.jar` as `compileOnly`, so no `com.fujitsu_ten` classes end up in the APK

## Disclaimer

This is a private hobby project and is not affiliated with Honda, Fujitsu Ten, Google or the
OpenDroidAuto authors. It changes system settings of the head unit. Use it at your own risk.
