# Deskflow Droid

**Use your computer’s mouse and keyboard on an Android phone over your local network.**

[简体中文](README.zh-CN.md) · [Download APK](https://github.com/nmdft/deskflow-droid/releases) · [Security](SECURITY.md)

An independent experimental fork of [ITESaurabh/deskflow-droid](https://github.com/ITESaurabh/deskflow-droid), used with a desktop [Deskflow](https://github.com/deskflow/deskflow) server. It is **not an official Deskflow release**. Your computer is the server and Android is the client: this does not share an Android mouse back to a computer, mirror the phone screen, or transfer files.

> **Experimental:** v1.1.0 is a prerelease. Builds and automated checks passed, but the signed APK with the new package ID has not completed fresh real-device interaction testing. Earlier device checks used a different test package on a Redmi K40 / Android 13. Other devices, Android versions and desktop-server versions are not a validated compatibility matrix.

## Why use it?

- **One desk, one mouse:** move into the Android screen configured in Deskflow; the client renders a pointer and translates clicks, drags and scrolling into accessibility gestures.
- **Keep your phone keyboard:** on Android 13+, supported text editors use accessibility input connections without replacing your default input method. This is editor input, not a system-wide hardware keyboard.
- **Adjust for a phone stand:** choose automatic, landscape or portrait pointer coordinates, with a 180° option in fixed modes; this does not force every app to rotate.
- **Optional mouse-entry wake:** enabled by default and saved immediately. Requests screen wake only when an entry event arrives; never unlocks the phone.
- **Local connection:** no root, ADB helper, account or cloud service is needed for normal operation. Text clipboard exchange and optional certificate-pinned TLS are implemented, with the limits below.

## Download and install

1. Open [Releases](https://github.com/nmdft/deskflow-droid/releases), select **v1.1.0 — experimental**, and download `deskflow-droid-v1.1.0.apk`. The GitHub-generated **Source code** archives are for developers, not installers.
2. Android 11+ (API 30) is the installation floor; **Android 13+ is recommended for enhanced editor input**. Android 11/12 use a more limited accessibility text fallback and are not device-validated here.
3. Allow APK installation for the app you use to open the file. Install and launch Deskflow Droid. This is a sideloaded APK, not a Play Store release.
4. In Android settings, enable the Deskflow Droid accessibility service. Some devices block this for sideloaded apps: inspect the source and permissions first, then use Android’s app-info “Allow restricted settings” option if offered. If notifications are requested, allow them to see connection status.
5. On your computer, install Deskflow, enable its **server** mode and add an Android screen to its layout. Match that screen name with the Android client name (default `android`).
6. Enter the computer’s LAN address and port (default `24800`) in the Android app. Both ends must agree on TLS. TLS defaults **off** in this client; enabling it at both ends is recommended. Independently compare the first-use certificate fingerprints before approval.
7. Connect, move the computer pointer across the configured screen edge, and test clicks and typing in an ordinary editable text field.

Use only a trusted LAN/VPN. Do not expose port 24800 to the public internet. Android/OEM background restrictions can interrupt the connection; if necessary, review the device’s battery restrictions for this app. Always-on operation is not guaranteed.

### Versions, updates and signing

| Item | Published v1.1.0 APK |
| --- | --- |
| Status | Experimental prerelease |
| Application ID | `cn.nmdft.deskflowclient` |
| Version name / code | `1.1.0` / `11` |
| Android floor | Android 11; enhanced editor input requires Android 13+ |
| Signing | Maintainer’s independent release key; not the upstream or debug key |
| Exact corresponding source | [v1.1.0 tag](https://github.com/nmdft/deskflow-droid/tree/v1.1.0) |

The fork can coexist with upstream and the older `.fix` test package. Settings, TLS identities and trust pins are not migrated. Debug builds use `cn.nmdft.deskflowclient.debug` and a different signature. The UI is currently mixed Chinese/English; bilingual documentation does not mean the application is fully localized.

A self-managed release signature is sufficient for APK distribution; it is not a Play certification or security endorsement. Future in-place updates require the same application ID, signing identity and an acceptable version code. Keep the signing key private and backed up separately; losing it prevents normal signed upgrades. A differently signed build cannot replace this installation without uninstalling it first, which loses local settings and identity. App backup/device transfer is disabled.

**APK-signing certificate SHA-256** (not the per-device TLS fingerprint):

```text
c1b88911e69d37bad79053e122120061f48b3ea14f8352b5784e8f1f9fc01fdc
```

The release contains only the APK and `SHA256SUMS.txt` as maintainer-uploaded assets. To check file integrity after downloading both into the same directory:

```sh
shasum -a 256 -c SHA256SUMS.txt
# Linux alternative: sha256sum -c SHA256SUMS.txt
```

Android SDK users can check the APK certificate with `apksigner verify --print-certs deskflow-droid-v1.1.0.apk`. A checksum detects a changed file; it is not an independent authenticity guarantee if obtained from the same compromised source.

## Capabilities and limits

| Area | What to expect |
| --- | --- |
| Mouse | Pointer overlay and accessibility gestures; not raw hardware mouse injection. Games, custom canvases and all multitouch interactions are not guaranteed. |
| Keyboard | Best in supported text editors on Android 13+. Basic editing/navigation and implemented copy/cut/paste shortcuts are not a promise of every hardware shortcut, key-up behavior or arbitrary app support. |
| Chinese / IME | Your default IME remains selected. Desktop-committed text and app/editor behavior matter; desktop key events are not guaranteed to drive the phone IME’s pinyin composition. |
| Clipboard | Text only, with bounded payloads. Desktop-to-phone reception is implemented; phone-to-desktop works through permitted reads or the client’s copy/cut selection path. Arbitrary background copies, images and files are not guaranteed. |
| Wake | Best effort while the service, Wi-Fi and connection remain alive. Deep sleep, OEM power management and process death can prevent entry events; no lock-screen bypass. |
| Servers | Intended for Deskflow. Related protocol handling does not establish tested Barrier, Synergy, Input Leap or all Deskflow-version compatibility. |

Accessibility can inspect focused text and perform gestures; clipboard text may reach the configured desktop. Password checks suppress selected-text forwarding in the copy/cut path; they do not mean every input path excludes password editors. This is not a formal security audit. TLS private identity and trust pins are stored in app-private preferences, not Android Keystore; backup is disabled. See [SECURITY.md](SECURITY.md) before sensitive use.

## Troubleshooting

- **Cannot connect:** check server mode, matching screen name, LAN address, port, firewall and matching TLS settings. Do not solve this by opening the port publicly.
- **Connected but no input:** recheck the accessibility service, unlock the device manually and try a normal text field. A successful TCP connection is not proof an app supports editor input.
- **Certificate changed:** verify the desktop certificate independently before forgetting/reapproving a pin. Never blindly accept an unexpected fingerprint.
- **Pointer direction is wrong:** use the pointer-direction controls; fixed mode has a 180° reverse option.
- **Screen stays dark:** enable “鼠标移入时亮屏”, then check whether the connection survived sleep. This does not unlock a locked phone.
- **An update will not install:** check package ID, signer and version code. Upstream, old test packages and independently built APKs are not interchangeable upgrades.

## Architecture and source layout

```text
Desktop Deskflow server
  └─ trusted LAN/VPN: TCP or optional TLS
      └─ DeskflowClient + Protocol (connection and input/clipboard messages)
          └─ InjectorService (Android accessibility service)
              ├─ pointer overlay + gesture dispatch + PointerSpace
              ├─ AccessibilityKeyboard / ImeKeys (Android 13+ editor input)
              ├─ ClipboardSync (best-effort text exchange)
              └─ ScreenWake (saved mouse-entry wake preference)
```

`MainActivity` provides connection/settings UI; `KeepAliveService` maintains a foreground-service notification. `Tls` handles identity and pinned server trust. Production sources live in `app/src/main`, JVM regression tests in `app/src/test`, and device tests in `app/src/androidTest`.

The current branch removes the unused legacy shell/UHID helper, its launch scripts, dead input classes and exploratory/template tests. It retains the working accessibility implementation, build files, resources, meaningful regression tests, licenses and attribution. **The published v1.1.0 APK is unchanged; its tag still preserves all corresponding source, including the old helper.** Branch cleanup is not deletion from Git history. No signing material, device-state files or private test recordings are intended for publication.

## Build and verification

Use the committed Gradle wrapper, a JDK 25 toolchain and an Android SDK with `platform-tools`, `platforms;android-37.0` and `build-tools;37.0.0`. Set `ANDROID_HOME` or a private `local.properties` SDK path. Gradle can download the configured daemon toolchain; offline builds need dependencies provisioned in advance.

```sh
sh gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease --console=plain
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Release output: `app/build/outputs/apk/release/`; without signing environment variables it is unsigned and is **not** the downloadable signed release.
- Optional local signing reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Supply them privately; never commit them, print them in logs or place the keystore in this repository.
- GitHub Actions runs checks/builds without signing credentials; it does not publish signed releases.
- Device tests need an appropriate connected device and, for some tests, explicit server/fixture setup. Compiling instrumentation tests does not run them. Wake tests may turn the device screen off.

See [AUDIT.md](AUDIT.md) for version-specific evidence and remaining acceptance checks. Automated build success does not establish everyday input reliability.

## Contributing and license

Report the app version, Android/OEM version, desktop-server version and reproduction steps. Redact host addresses, device IDs, clipboard contents, certificates/private keys and tokens from logs. Security reports follow [SECURITY.md](SECURITY.md).

Based on ITESaurabh’s project at revision `28f80c7bbe340ce883df04ac1e5317acd4fa7702`; see [NOTICE.md](NOTICE.md) for changes and provenance. Licensed under **GPL-2.0-only WITH LicenseRef-OpenSSL-Exception**; the original [LICENSE](LICENSE) and [exception](LICENSES/LicenseRef-OpenSSL-Exception.txt) are preserved. No official Deskflow affiliation or trademark ownership is claimed.
