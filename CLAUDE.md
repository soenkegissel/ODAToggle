# Projekt: ODAToggle („AA Sound“) – Sound-Umschalter für OpenDroidAuto auf Honda Connect

## Kontext
Ich fahre einen Honda CR-V (2017) mit Honda Connect (Fujitsu Ten „Display Audio“, Android 4.0.4, API 15).
Darauf läuft OpenDroidAuto (https://github.com/hirneagabriel/OpenDroidAuto, Paket `it.smg.hu`) als
Android-Auto-Head-Unit. Welche Audio-Rechte eine App hat, legt eine System-Whitelist des Radios fest,
die man sonst mit S_Mikes „Honda Permissions“-App (`com.xda_user.honda.permissions`) bearbeitet.

Problem: Je nach Einstellung spielt OpenDroidAuto entweder
- **exklusiv** (Sound Out = 1, Sound Interrupt = 0, Interrupt Mute = 0): Stereo auf beiden Lautsprechern, Radio pausiert, oder
- **gemischt** (Sound Interrupt = 1): parallel zum Radio, aber nur über den linken Lautsprecher (Kanal für Navi-Ansagen).

Einen Modus „Stereo + gemischt“ gibt es in der Firmware nicht. Das zeigt das Reverse Engineering der
Permissions-App: Pro Prozess gibt es nur authType, appType, videoOut, soundOut, soundInterrupt,
soundInterruptMute, lastMode, oomSetPerm, processKillTarget und 12 reservierte Bytes, aber kein Balance-
oder Kanal-Feld. Die Honda-Medienquellen nutzen soundOut=1/interrupt=0, die Navi nutzt soundOut=0/interrupt=1/mute=1.

## Was die App macht
`com.rucksack.odatoggle` (Label „AA Sound“) ist ein Widget mit Umschalt-Button plus eine unsichtbare
Launcher-Activity. Ein Tipp schaltet den Whitelist-Eintrag von `it.smg.hu` um:
- Mix → Exklusiv: die aktuellen Werte für soundOut/soundInterrupt/soundInterruptMute in SharedPreferences
  sichern, dann 1/0/0 setzen.
- Exklusiv → Mix: die gesicherten Werte wiederherstellen (Standard 1/1/0).
- Danach den Broadcast `com.fujitsu_ten.displayaudio.whitelist.common.intent.action.WHITE_LIST_UPDATE`
  mit dem Extra `white_list_update_count` senden.

Dateien (`app/src/main/java/com/rucksack/odatoggle/`):
- `WhiteList.java`: lädt und speichert die gesamte Whitelist über die System-API `IWhiteList`
- `SoundToggle.java`: Logik zum Umschalten und Zustand
- `ToggleWidget.java`: AppWidgetProvider (RemoteViews, Farbe und Text je nach Zustand, Toast-Meldungen)
- `ToggleActivity.java`: Theme.NoDisplay, schaltet um und beendet sich sofort

## System-API (liegt bereits auf dem Radio, NICHT mitpacken)
`com.fujitsu_ten.displayaudio.whitelist.common.IWhiteList` (native lib `ada-ext_jni`), öffentliche Methoden:
`loadServiceInfo(byte[8])`, `getAllowedInstallationList()`, `getAppPriorityList()`,
`getProcessControl(String processName, String[] packageNames)`, `getUpdateCount()`, `update(byte[])`.
Kompiliert wird gegen `app/libs-compile/ada-ext-api.jar` (aus OpenDroidAuto `ext-libs/framework`), eingebunden
mit `compileOnly`. Die Klassen dürfen nicht im Dex landen: Eigene Kopien mit abweichenden Signaturen
haben vorher Probleme gemacht.

Version aus loadServiceInfo: `(b[0]&0xFF)<<16 | (b[1]&0xFF)<<8 | (b[2]&0xFF)`.

Format von `update()` (aus S_Mikes App übernommen), Puffer immer 0xFFFF Bytes:
- [0..1] Gesamtlänge, big endian
- [2..3] Länge des ProcessControls-Blocks, ab [4] der Block selbst:
  - 2 Byte Anzahl, dann pro Eintrag:
  - len+processName, len+packageName
  - authType, appType, videoOut, soundOut, soundInterrupt, soundInterruptMute, lastMode, oomSetPerm
  - processKillTarget (aus AppPriority)
  - 12 Byte reserved
- danach 2 Byte Länge des AllowedInstallations-Blocks und der Block selbst:
  - 2 Byte Anzahl, dann pro Eintrag: len+apkFileName, 0x00, sigDigestSha256 (raw)
- danach 3 Byte Version.
Erfolgreich ist das Schreiben, wenn `getUpdateCount()` danach größer ist als vorher.

## Build-Einstellungen (wichtig für Android 4.0.4)
- AGP 8.5.2, Gradle 8.7, JDK 21 (nur auf dem Build-Rechner); compileSdk 34
- minSdk 15, **targetSdk 15** (wie OpenDroidAuto), Java source/target 1.8, minifyEnabled false
- Lint: `ExpiredTargetSdkVersion` und `OldTargetApi` deaktiviert, `checkReleaseBuilds false`
- App-Icon als **PNG** (drawable-mdpi bis xxhdpi). Ein XML-Drawable als Icon ließ den Installer des Radios abstürzen.
- Keine AndroidX- oder Support-Bibliotheken
- Ausgabename immer `HondaAppCenter_A1.apk` (über `applicationVariants.all { outputs.all { outputFileName = ... } }`);
  nur unter diesem Namen installiert das Radio APKs von einem USB-Stick
- Signiert mit meinem RSA-2048-Release-Key (v1 + v2; Android 4.0 braucht v1/SHA-1, das wählt AGP bei minSdk 15 automatisch)

## Installation auf dem Radio
1. `HondaAppCenter_A1.apk` per USB-Stick installieren, genauso wie OpenDroidAuto
2. In der Permissions-App einen Eintrag `com.rucksack.odatoggle` anlegen: Auth Type PREINSTALL, App Type OTHER,
   Video Out STILL_IMAGE, alle Sound-Häkchen aus
3. Widget platzieren oder das Icon „AA Sound“ antippen

## Entscheidungen (25.11.2026)
- Sprachen: Englisch ist Standard (`values/`), dazu de, zh, hi, es, fr, ar, bn, pt, ru, ur. Grund: Das
  Projekt kommt auf GitHub; gewünscht waren die 10 meistgenutzten Sprachen. Jeder neue String in alle 11 Dateien.
- Fehlermeldungen laufen über `ToggleException` (String-Ressource), kein fester Text im Code.
- Signatur: `app/build.gradle` liest die git-ignorierte `keystore.properties` (storeFile, storePassword,
  keyAlias, keyPassword). Ohne die Datei baut `assembleRelease` eine unsignierte APK.
- Die signierte APK liegt im Repo unter `app/release/HondaAppCenter_A1.apk` (Wunsch: APK im Repo;
  der Ordner ist das Standardziel des Studio-Assistenten). Nach jeder Code-Änderung neu bauen und ersetzen.
- S_Mikes Permissions-App 1.1 liegt unverändert unter `third-party/honda-permissions/HondaAppCenter_A1.apk`
  (Wunsch des Besitzers, damit alles Nötige an einem Ort ist). Fremde App, keine bekannte Lizenz zur
  Weitergabe: bei Einspruch von S_Mike entfernen und nur verlinken.

## Stand
Funktioniert auf dem Radio. Bei Änderungen bitte die Build-Einstellungen oben einhalten und vor jeder
Auslieferung die APK prüfen:
- `apksigner verify --min-sdk-version 15`
- Dex-Version 035
- keine `com/fujitsu_ten`-Klassen im Dex
- PNG-Icon vorhanden
Mit dem aktuellen OpenDroidAuto-Release vergleichen, wenn der Installer Probleme macht.
