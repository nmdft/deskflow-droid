# Security and privacy

- Use only on a trusted LAN/VPN. Do not expose port 24800 publicly.
- TLS is optional and defaults off. Enable TLS at both ends and independently
  compare first-use SHA-256 certificate fingerprints before trusting a connection.
  Plain TCP exposes input and clipboard traffic to network observers.
- Accessibility access can inspect focused editable text and perform gestures.
  Clipboard text may be sent to the configured desktop server. Password checks
  suppress copy/cut selected-text forwarding; they do not exclude password editors
  from every input path. This is not a formal security audit.
- TLS identity and pinned trust are stored in app-private data. Backups and device
  transfer of app storage are excluded; uninstalling loses settings and identity.
- There is no analytics or cloud account integration added by this fork. Inspect
  the source before entrusting sensitive input to an experimental client.
- No signing key, local device state, personal serial, host configuration or test
  recording is distributed. Do not post secrets, clipboard contents, device IDs or
  unredacted logs in issues. Use GitHub private vulnerability reporting if enabled;
  otherwise open a metadata-only issue requesting a private contact channel.
- The current branch no longer includes the unused legacy ADB/UHID helpers. Their
  historical source remains at the v1.1.0 tag to match the published APK; they are
  not required or supported for normal accessibility operation.
- An APK-signing certificate identifies a release signer; it is separate from the
  per-device TLS identity/fingerprint. Never publish either private key. Current
  TLS identity storage uses app-private preferences, not Android Keystore.
