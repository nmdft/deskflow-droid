# Public release audit — 2026-10-17

Scope: this public snapshot and APK, not an exhaustive independent security review.

## Privacy / provenance

- Clean source snapshot from ITESaurabh/deskflow-droid revision
  28f80c7bbe340ce883df04ac1e5317acd4fa7702 plus local modifications.
- Historical upstream IDE device metadata is not imported into this repository.
- IDE state, local-test data, recordings, private notes, local.properties, build
  output and signing credentials are excluded from version control.
- Removed device-specific selection/fallback from the legacy ADB helper.
- Gitleaks 8.30.1 directory scan: zero findings. Additional pattern checks for
  personal paths, saved device ID, private keys, token formats and private LAN
  host configuration: no findings in the publication snapshot.
- Image assets and Gradle wrapper JAR are inherited from upstream. License,
  original author, source revision and modifications are recorded in NOTICE.md.
- Backup disabled and cloud/device-transfer storage exclusions added to prevent
  copying TLS private identity, trust pins and experimental helper credentials.

## Build checks

Command: `sh gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease --console=plain`

- Clean snapshot build: SUCCESS.
- JVM tests: 56 passed, 0 failures/errors/skips.
- Android instrumentation APK: compiled, not run for this new package.
- Android lint: 0 errors, 39 warnings, 1 hint. Existing warnings remain; this is
  not a warning-free claim. LocalClipboardManager deprecation remains.
- Signed release: cn.nmdft.deskflowclient, versionName 1.1.0, versionCode 11.
- apksigner verification: PASS, APK signature scheme v2, RSA 3072.
- Certificate SHA-256: `c1b88911e69d37bad79053e122120061f48b3ea14f8352b5784e8f1f9fc01fdc`.
- APK SHA-256: `81e55537e2253561d2c26732360cdf09d4428b2d50cdf789d37befcfc2bb8bee`.
- Signing material stays outside the repository on the maintainer's local
  machine; not uploaded to GitHub or configured in Actions secrets.

## Limits / release gate

This is an experimental release, not a claim of production readiness. Earlier
K40 / Android 13 tests covered the previous test package and wake setting;
new-package install, permission onboarding, reconnection, clipboard and sustained
screen-off operation still require fresh real-device acceptance testing. No
universal Android/OEM compatibility, Play approval or arbitrary-keyboard support
is claimed. See README.md and SECURITY.md before use.
