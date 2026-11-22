# Changelog

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
