# Prism Gallery

Native Android gallery — Kotlin, Jetpack Compose, Material 3 — with a liquid-glass UI.
MVVM + Clean-ish layering, Hilt, Room, DataStore, Coil, Media3, WorkManager. Min SDK 26, target 35.

> **Status: scaffold, not yet compiled.** Every file was written without a Gradle build available. Expect to fix some compile errors on first sync. Run `gradle ktlintFormat` before the first CI run.

## Setup
1. Install Android Studio (Ladybug+) and JDK 17.
2. Open the folder; let it sync. No Gradle wrapper is included — run `gradle wrapper --gradle-version 8.10.2` once (or let Android Studio generate it) and commit it.
3. Run on a device/emulator (API 26+). Grant photo/video access on first launch.

## APK size
CI publishes a single arm64 release APK (R8 + resource shrinking, English-only resources, unbundled ML Kit OCR). With no keystore secrets it is signed with the debug key so it is still installable.

## CI
`.github/workflows/android.yml` has one job: build the release APK and upload it as an artifact. No secrets or keys are needed; the APK is signed with the debug key so it installs directly.

## Structure
```
app/src/main/java/com/prism/gallery/
  GalleryApp.kt  MainActivity.kt
  di/            Hilt module (Room)
  data/          media (MediaStore), db (Room), vault (AES-GCM, PBKDF2), sync (WorkManager/WebDAV), SettingsRepository
  domain/        grouping, sorting, search filters
  ui/theme       ThemeSettings (serializable → export/import), palettes, GalleryTheme
  ui/components  GlassSurface, GlassBackdrop, haptics, shared-element hooks
  ui/nav         AppRoot: bottom glass bar (compact) / glass rail (medium+), NavHost
  ui/photos albums viewer editor video search vault settings
.github/workflows/android.yml   CI/CD        fastlane/   Play deploy        logo.svg
```

## Feature status
| Area | State |
|---|---|
| Glass design, themes, presets (save/export/import), sliders, dynamic colour, 11 accents incl. colour-blind-safe | Implemented |
| Adaptive icon + themed (monochrome) icon, logo SVG | Implemented |
| Photos/videos grid, grouping (day/month/year), sort, pinch density, drag-select, favorites, albums | Implemented |
| Trash (system 30-day trash, Android 11+) | Implemented; no Room fallback for API 26–29 |
| Viewer: pager, pinch/double-tap zoom, swipe-down close, EXIF, slideshow, wallpaper, share | Implemented |
| Editor: rotate, exposure/warmth/contrast/saturation, filters, non-destructive params, export copy | Implemented |
| Editor: crop, markup | **Not done** |
| Video: speed 0.25–5×, hold-boost, brightness/volume/seek swipes, ±10 s, zoom, A–B, sidecar subs, audio-track cycle, PiP, frame capture, lock | Implemented (untested on device) |
| Video: background audio (needs a Media3 `MediaSessionService`), mini player | **Not done** |
| Search: filters, saved/recent, on-device OCR + labels (ML Kit, on-demand scan of 150 items) | Implemented; index is in-memory |
| Private space: PIN/password, biometric, AES-256-GCM vault (Keystore), decoy PIN, FLAG_SECURE, auto-lock, shred, disguise alias | Implemented (images; videos need a decrypt-to-stream player) |
| Pattern lock, break-in selfie (CameraX) | **Not done** (hook marked TODO) |
| Cloud sync: WorkManager + Wi-Fi/charging constraints, WebDAV upload with keep-both conflicts | Scaffolded; credentials UI and encrypted storage missing |
| Google Drive, S3, end-to-end encryption, storage dashboard | **Not done** |
| Map view | **Not done** (needs a maps SDK + API key) |
| Two-pane detail, foldable posture handling | Rail + window size classes only |
| TalkBack labels, 48 dp targets, reduce motion, high contrast | Implemented; AI image descriptions not done |
| Languages | Per-app locale picker (Android 13+); only English strings shipped |
| Print, backup/restore, default-folder picker | **Not done** |
| RAW | Decoded through the platform decoder (DNG widely; CR2/NEF/ARW are device-dependent). Exposure/WB are applied to the decoded preview, not the sensor data |

## Notes
- Compose has no native backdrop blur. `GlassBackdrop` blurs coloured orbs behind translucent panels (real blur on Android 12+); panels are tinted gradients with glowing borders.
- HEIC/HEIF and 4K/8K HEVC rely on device hardware decoders.
- Shared-element transitions are wired through `LocalSharedScope`/`sharedImage()`.
