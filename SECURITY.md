# Security and privacy

- Use only on a trusted LAN/VPN. Do not expose port 24800 publicly.
- TLS is optional and defaults off. Enable TLS at both ends and independently
  compare first-use SHA-256 certificate fingerprints before trusting a connection.
  Plain TCP exposes input and clipboard traffic to network observers.
- Accessibility access can inspect focused editable text and perform gestures.
  Clipboard text may be sent to the configured desktop server. Password fields are
  excluded from the editor/selection path, but this is not a formal security audit.
- TLS identity and pinned trust are stored in app-private data. Backups and device
  transfer of app storage are excluded; uninstalling loses settings and identity.
- There is no analytics or cloud account integration added by this fork. Inspect
  the source before entrusting sensitive input to an experimental client.
- No signing key, local device state, personal serial, host configuration or test
  recording is distributed. Do not post secrets, clipboard contents, device IDs or
  unredacted logs in issues. Use GitHub private vulnerability reporting if enabled;
  otherwise open a metadata-only issue requesting a private contact channel.
- Legacy ADB/UHID tools require an authorized debug build and a device supporting
  the shell backend. They are experimental, not required, and not a supported
  workaround for arbitrary Android input restrictions.
