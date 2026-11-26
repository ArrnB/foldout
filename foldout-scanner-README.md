# Foldout — Document Scanner

A browser-based document scanner: capture a page, straighten it, apply a filter, optionally watermark it, and export a PDF or image set. Everything — camera capture, perspective correction, filtering, watermarking, PDF generation — runs **entirely on-device**. No upload, no account, no network calls.

Single self-contained HTML file (`foldout-scanner.html`). Open it in a mobile browser (Chrome/Safari) — no install step.

## Features

### Capture
- "Scan page" opens a choice of **Take Photo** (direct camera launch) or **Choose from Library** (existing photo)
- Works via native `<input type="file">`, so it's compatible across iOS/Android browsers without any permissions setup beyond the OS camera prompt

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

- No automatic edge detection — crop corners start at a fixed inset and must be dragged into place by hand
- JPG export downloads one file at a time rather than as a single zip
- Tested primarily on mobile Chrome/Safari; desktop browsers will work but the UI is optimized for a phone-width screen

## Possible next steps

- Automatic edge/border detection (e.g. via a lightweight on-device CV model)
- OCR / searchable-text layer in the exported PDF
- Zip bundling for multi-image export
