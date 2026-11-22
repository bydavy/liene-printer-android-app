# Changelog

## [0.1.1] - 2026-11-21

### Added

- A final 2:3 preview and confirmation dialog before a print job starts,
  showing resolution, framing, copies, printer, and low-battery warning.
- Android launcher icon based on the desktop app's photo-in-printer artwork.
- Hardware-tested printing on a Pixel 11 Pro (Android 17) with an N200 Pro.

### Fixed

- Changing the system light/dark theme no longer reloads the shared photo and
  resets rotation, framing, or zoom.
- Battery status now maps the printer's 0–4 level to empty/low/50%/75%/full;
  level `4` was previously mislabeled as 4%.

### Changed

- Print resolution is fixed at 1200×1800; the 600×900 quality selector was
  removed.

## [0.1.0] - 2026-11-21

### Added

- Share target for `image/*`, so any gallery app can send a photo to Liene.
- Mobile-first Compose editor with rotate, Fit/Fill framing, pinch-to-zoom,
  drag-to-pan, and a live 2:3 print preview.
- 600×900 and 1200×1800 output, copies 1–9, JPEG compressed to the printer's
  ~1 MiB transfer target.
- Bluetooth Classic SPP transport with SPP and direct-RFCOMM-channel fallbacks.
- Protocol port: frames, checksums, JSON-RPC, status/info, job upload and
  tracking, resume/cancel.
- Foreground `connectedDevice` print service with notification progress.
- Light/dark Material 3 theme and diagnostics log.
- CI and tag-based APK release workflows.
