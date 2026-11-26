# Foldout — Document Scanner

A browser-based document scanner: capture a page, straighten it, apply a filter, optionally watermark it, and export a PDF or image set. Everything — camera capture, perspective correction, filtering, watermarking, PDF generation — runs **entirely on-device**. No upload, no account, no network calls.

The whole scanner is one self-contained HTML file (`foldout-scanner.html`). There are three ways to use it:

- **In the browser:** open **https://arrnb.github.io/foldout/** on a phone. Nothing to install.
- **As an installed web app:** in Chrome, open the menu and choose *Install app* (or *Add to Home screen* on iPhone). It then opens full-screen and works offline.
- **As an Android app:** the `android/` folder wraps the same page in a small native app (about 300 KB, Android 10+). See [Android app](#android-app) below.

## Android app

Putting it on the Google Play Store: see [PLAY_STORE.md](PLAY_STORE.md).

`android/` is a minimal Kotlin app that shows `foldout-scanner.html` in a WebView. The HTML is copied in from the repository root at build time, so the website and the app are always the same scanner. The app adds only what a web page cannot do by itself:

- **Take Photo** opens Foldout's own live camera (see *Live camera* above); **Choose from Library** opens the system photo picker
- **Save as PDF / images** writes to *Downloads/Foldout* (through MediaStore, so no storage permission)
- The **Back** button steps back through the scanner's screens and closes the app from the home screen
- If the camera permission is refused, Take Photo falls back to the photo picker
- If Android stops the app while the phone's camera app is open (common on low memory), the photo is still delivered when it comes back

It asks for **one permission, the camera**, for the live viewfinder, and only the first time you tap Take Photo. Frames are looked at on the phone and never leave it. It has **no internet permission**: the page is bundled inside the app.

Build it with Android Studio (open the `android/` folder), or from the command line:

```
cd android
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # shrunk with R8, ~300 KB (sign it before installing)
```

GitHub Actions builds the app on every push and attaches the debug APK to each run.

## Features

### Capture
- "Scan page" opens a choice of **Take Photo** or **Choose from Library** (existing photo)

### Live camera with auto-capture
- **Take Photo** opens a live viewfinder inside Foldout. About seven times a second the picture is run through the page detector, and the page is outlined as it comes into frame
- With **Auto** on (the switch at the top right, remembered), once the page has been found with confidence and holds still for about a second, the ring on the shutter fills and the photo is taken by itself, then cropped and straightened straight away
- The shutter always works by hand; an outline the detector is unsure of opens the corner screen instead of being cropped blindly
- Where the live camera can't open (permission refused, older browsers), Take Photo uses the phone's normal camera route instead
- For testing without a camera, `foldout-scanner.html?fakecam=tests/scenes/persp_a.jpg` plays a picture as the camera feed (it slides in, then shakes slightly)

### Automatic page detection
- When a photo comes in, Foldout finds the page and puts the four corners on it
- When it is sure (score ≥ 0.95), it straightens the page straight away and skips the corner screen; **Back** returns there if you want to adjust. Switch this off with *Auto-crop when a page is found* on the corner screen
- When it is not sure, the corner screen opens with its best guess; when it finds nothing, the corners start at the usual default
- Runs on a 320 px copy of the photo in plain JavaScript (no model, no library, about 20–60 ms), so it works offline and in the app
- How it works: two ways of separating page from background (a brightness split with holes filled in, and an outline traced from brightness edges), the largest four-sided shape inside each, each side straightened with a RANSAC line fit (so a thumb over a corner or a dog-ear is ignored) and moved onto the sharpest brightness edge, then the candidate whose sides run along real edges wins
- Tested on 18 synthetic scenes with known corners (rotated, perspective, low light, white-on-white, shadows, thumbs, clutter, cut-off pages, no page): 17 pass, with corners within 0.4% of the photo's diagonal (1% for the page cut off by the photo's edge). The miss is two overlapping sheets, which merge into one outline; it is flagged as unsure, so the corner screen opens. `tests/` has the generator and the test, and CI runs them

### Crop & straighten
- Drag 4 corner handles to trace a page's actual edges over a photo taken at an angle
- 4 additional edge-midpoint handles let you nudge a whole side (constrained to one axis) instead of lining up two corners by hand
- A magnifier loupe appears while dragging, showing a zoomed, brightened view of exactly what's under your finger — repositions itself above or below your finger so it's never covered
- Pinch-to-zoom (2 fingers) lets you inspect the image closely; the photo itself never pans/drifts, so corner placement stays precise
- On confirm, a real **perspective (homography) warp** straightens the page — not a simple rectangular crop

### Filters
- Original, Enhance (contrast/brightness), B&W, Grayscale
- Live thumbnail previews for each option

### Watermark
- Free text, applied automatically once you type something (no separate on/off switch)
- **7 placement styles**: Center diagonal, Straight center, Tiled repeat, Corner label, Footer band, Top band, and **Custom** (drag directly on the preview to position it anywhere, plus a rotation slider)
- **6 fonts**: Sans, Serif, Mono, Stamp, Script, Condensed
- Size and transparency sliders (persist independently of which placement style is selected)
- **8 color presets** (gray, black, cream, red, orange, green, blue, purple) + a rainbow-ringed custom color picker
- **Per-document or per-page**: a document-wide watermark applies to every page by default; toggling "Apply to every page" off lets a single page carry its own watermark instead. A dropdown ("Different watermark per page") lists every page's current status and jumps you straight into editing any one of them
- A fullscreen inspector (expand icon) lets you check placement/legibility up close with its own pinch-zoom

### Document management
- Multi-page documents with reorder (left/right), delete, and per-page filter/watermark re-editing — all from the home screen's page grid
- Home screen thumbnails show the resolved watermark (global or page-specific override) so you can see the real output before exporting

### Export
- **PDF**: all pages combined into one file, sized to A4
- **Images**: each page saved as an individual JPG
- Both honor each page's resolved watermark at full export resolution (not just preview resolution)
- Pages without a watermark go into the PDF exactly as stored — no second round of JPEG compression

### Keeps your scan
- Pages, watermark settings and the title are saved in the browser's IndexedDB (on this device only) as you work, so a refresh, a closed tab, or the phone killing the browser in the background no longer loses a scan
- **New** clears it

## Privacy model

Everything — image capture, the perspective warp, filters, watermark compositing, and PDF assembly — happens in the browser using Canvas and plain JavaScript. The PDF is written by a small built-in writer (a JPEG can go into a PDF as-is), so the page loads **no external scripts and makes no network requests at all**; it works offline once opened. Saved scans stay in this browser's storage on this device.

## Performance notes

- Very large camera photos (50–200 MP) are scaled to 4096 px on the long side when loaded, which keeps them inside browser canvas limits; the straightened page is capped at 1500 px anyway
- Photos are read through object URLs rather than base64 strings
- The filter screen builds its four preview chips from a 160 px copy and only filters the full page for the filter you pick
- Watermark sliders and drags redraw at most once per frame
- Home-screen thumbnails are drawn at 600 px instead of full page size
- The perspective warp's per-pixel loop is unrolled and samples pixel centres

## Known limitations

- Page detection can merge overlapping sheets and is untested on real-world photos of unusual pages (very glossy, see-through, or the same colour as the table); the corner screen is always there to fix it
- JPG export downloads one file at a time rather than as a single zip
- Tested primarily on mobile Chrome/Safari; desktop browsers will work but the UI is optimized for a phone-width screen

## Possible next steps

- Shadow and glare removal on the straightened page
- OCR / searchable-text layer in the exported PDF
- Zip bundling for multi-image export
