# Public release audit and branch checks — 2026-10-17

Scope: this public snapshot and APK, not an exhaustive independent security review.

## Published v1.1.0: privacy / provenance

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

## Published v1.1.0: build checks

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

## Current branch: documentation and unused-source cleanup

- English README is the default; README.zh-CN.md provides the matching Chinese
  guide. Documentation distinguishes implemented behavior, earlier device evidence,
  and the signed APK’s still-pending real-device acceptance.
- Removed 13 unused/helper or exploratory/template files: shell/UHID launchers,
  helper server/build script, Python launcher, PhysicalKeyboard/HidKeyboard classes
  and their tests, UHID probe, IME exploration test and two template tests.
  InjectorService already uses the accessibility path and had no references to
  the deleted production classes. ImeKeys and meaningful regressions are retained.
- No new APK/version/tag: the published APK bytes and v1.1.0 source tag are
  unchanged. The original tag preserves full source corresponding to that binary;
  current-branch cleanup must not be described as removal from repository history.
- Same Gradle command as above: SUCCESS in 31 seconds; debug, instrumentation and
  unsigned release APKs compile. Current JVM suite: 48 passed, 0 failures/errors/
  skips (eight tests were removed with unused/template files, not silently lost).
- Lint: 0 errors, 39 warnings, 1 hint. Device tests compiled but were not run;
  no new-device, upgrade, or signed-release acceptance claim is made.
- Gitleaks 8.30.1 current directory scan: no leaks found. Additional current-source
  patterns for personal paths, known private device ID, private LAN hosts and
  credential/private-key bodies: no findings. Heuristics are not a security proof.
- LICENSE and OpenSSL exception match the v1.1.0 tag byte for byte. Local README
  links resolve. GitHub release lists exactly two maintainer-uploaded assets:
  deskflow-droid-v1.1.0.apk and SHA256SUMS.txt (plus GitHub-generated source archives).
- Downloaded release APK passes checksum and apksigner verification; certificate
  matches the fingerprint recorded above. Signature verification is not a fresh
  on-device installation or interaction test.
