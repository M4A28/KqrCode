# QR Scanner

A fast, private, offline-first QR & barcode scanner for Android. Everything —
detection, generation, history — happens on-device. No analytics, no tracking,
no network permissions.

## Features

**Scan**
- Live camera scanning (QR, EAN-13/8, UPC-A/E, Code 39/93/128, Codabar, ITF, PDF 417, Aztec, Data Matrix) with on-device ML Kit
- **Scan from gallery** — decode a code from any picture (camera-roll button or "Share → QR Scanner" from any app)
- Torch toggle, pinch-to-zoom, and tap-to-focus
- Instant feedback: beep, vibration, auto-copy, auto-save, auto-open URLs (all optional in Settings)
- **Scan session mode** — toggle the box icon to batch-collect codes without stopping; copy all, export CSV, or save the session when done
- **Smart actions** based on what you scanned:
  - `WIFI:` → *Join Wi-Fi* (network suggestion on Android 10+, settings fallback below)
  - `BEGIN:VCARD` / `MECARD:` → *Add contact* (prefills the Contacts editor)
  - `tel:` → *Call*, `mailto:` → *Email*, `SMSTO:` → *Send SMS* (body prefilled), `geo:` → *Open map*
- Base64 payloads are detected and can be decoded inline

**Create**
- 13 barcode formats (all offline, via ZXing)
- Templates for text, links, Wi-Fi, contacts, email, phone, SMS, and geo locations
- Social media links (Instagram, WhatsApp, X, TikTok, YouTube, Telegram, and more) automatically get the platform's logo in the QR center
- Every generated image carries `mohammedpro.vercel.app` under the code
- Live preview as you type, six code colors
- Share as PNG, save to gallery (`Pictures/QR Scanner`), or save to history

**History**
- Every scan and generated code is stored locally (Room)
- Search + filters: All / Scanned / Generated / Favorites / Duplicates
- Tap a row for a detail sheet with parsed actions, Base64 decoding, and PNG export
- Long-press to multi-select, delete with **Undo**, per-row favorites
- Export everything to CSV

**Localization**
- English and Arabic (RTL) out of the box; per-app language selection on Android 13+
- Add more languages by adding a `values-<locale>/strings.xml` folder

**Settings**
- Scanning & feedback toggles, front-camera support
- Light / Dark / System theme, six accent colors

## Tech stack

- Kotlin, Jetpack Compose (Material 3), edge-to-edge
- CameraX for the preview, ML Kit Barcode Scanning for detection
- ZXing for generation, Room for history, DataStore for settings, KSP
- Haze for the glass/blur panels

## Build

```bash
./gradlew assembleDebug        # debug APK
./gradlew test                 # unit tests
```

Requires JDK 17 and Android SDK 37 (compile) / 24 (min). Open in Android Studio
and run the `app` configuration on any device or emulator with a camera.

## Project layout

```
app/src/main/java/com/mohammed/mosa/qrscanner/
├── MainActivity.kt        # tabs, share-target handling
├── scanner/               # camera preview, analyzer, gallery decode, result UI
├── generate/              # format picker, templates, ZXing rendering
├── history/               # search, filters, multi-select, CSV export
├── settings/              # toggles, theme, accent
├── data/                  # Room (ScanStore), repository, settings, CSV
└── util/                  # content-type detection, smart actions, sharing
```

## Versioning

Versions are generated automatically from git — no manual bumps in
`build.gradle.kts`:

- **`versionCode`** = total commit count, so it grows with every commit.
- **`versionName`** = the highest `v*` tag: a clean `X.Y.Z` when built exactly
  on the tag, `X.Y.Z-N-g<hash>` for commits after it, and `1.5.0-N-g<hash>`
  before any tag exists.

To release: `git tag v1.5.0 && git push origin v1.5.0` — every build of that
commit (local or CI) reports `1.5.0`, and CI's release job publishes the
signed APK for it.

```bash
./gradlew showVersion                          # print the computed version
./gradlew assembleRelease -PappVersionName=2.0.0 -PappVersionCode=99   # override
```

CI checks out full history (`fetch-depth: 0`) so CI and local builds agree.

## CI

`.github/workflows/android-build.yml` builds a debug APK on every push and a
signed release APK for `v*` tags (signing credentials come from repo secrets).

## Privacy

The app has **no internet permission**. Camera frames are processed in memory
and never leave the device; history lives in the local database and can be
cleared or exported at any time.
