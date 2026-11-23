# Changelog

## [Unreleased]

### Changed

- **Breaking:** change the Android application ID and Kotlin namespace to
  `io.github.bydavy.openliene`. Android treats this as a new app, so uninstall
  the previous `io.github.bydavy.liene` build before installing this one.

## [0.2.0] - 2026-11-22

### Added

- Roborazzi + Robolectric screenshot and interaction tests for the collage
  editor, frame editor, print preview, single-photo editor, print confirmation,
  printer/job cards, and diagnostics, with committed golden images.
- Unit tests for printer status, alert, and job-state parsing.

### Changed

- Renamed the app to **Open Liene**. The launcher label, share-target name,
  notification title, and release APK name use the new name. The application ID
  remains `io.github.bydavy.liene` so existing installs update in place.

## [0.1.3] - 2026-11-22

### Added

- Collage mode for 2–4 photos: multi-photo share auto-opens it, with vertical,
  horizontal, focused, and grid layouts, live preview, and a print-preview
  screen for copies and final printing.
- Collage layout options use white/grey diagram icons instead of text labels.
- Per-photo zoom/pan directly in the collage preview, plus a frame editor with
  rotate, replace, and reset for one photo at a time.
- A Borders checkbox (on by default) and an explicit Randomize photos button.

## [0.1.2] - 2026-11-22

### Changed

- The photo editor, empty photo area, and final print preview now have square
  corners so the framing matches the printed paper.

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
