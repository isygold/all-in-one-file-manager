# FMX — All-in-One File Manager

Single-file, stdlib-only, mobile-friendly file manager in Python.
MT Manager + ZArchiver inspired. Runs in Termux, Linux, anywhere with Python 3.8+.

No dependencies. No build step. One file: `filemanager.py`.

## Features

- **Basic ops:** browse, mkdir, mkfile, rename, copy, cut, paste, delete, chmod, properties
- **Search & sort:** recursive name search, sort by name/size/date/type, hidden toggle
- **Previews:** images, video, audio, PDF, text/code (in-browser)
- **Power UX:** tabs, history, bookmarks, multi-select, dark/light mode, mobile-first touch UI, dual-pane on wide screens
- **Archives (ZArchiver-style):** create ZIP / TAR.GZ, extract ZIP/TAR, browse inside ZIP/APK/JAR, extract
- **MT-style tools:** APK inspector (dex/so/manifest, `aapt badging` if available), text editor, hex viewer, md5/sha1/sha256 checksums
- **Upload / download** via browser

## Run (Termux / Android)

```bash
# one-time: allow access to shared storage
termux-setup-storage

python3 filemanager.py --port 8080
# open in phone browser:
# http://127.0.0.1:8080
```

Listen on all interfaces (same Wi-Fi):

```bash
python3 filemanager.py --host 0.0.0.0 --port 8080
```

## Install as app

Two ways — pick whichever suits you:

**Option A — install from browser (fastest, no build).**
The web UI is a PWA. Run the server, open the URL in Chrome,
then *Menu → Add to Home screen / Install app*. It launches
fullscreen with its own icon.

**Option B — real APK (standalone, no Termux needed).**
Every push to `main` builds a debug APK with the Python backend
embedded (Chaquopy + WebView) via GitHub Actions:

1. Open the repo on GitHub → **Actions** → latest **Build APK** run.
2. Download the **fmx-debug-apk** artifact, install it on your phone.
3. Grant **All files access** when asked — the app serves the same
   FMX UI from `http://127.0.0.1:8080` internally.

## Run (Linux / PC)

```bash
python3 filemanager.py --port 8080
```

## API

JSON API under `/api/*`:
`roots, list, search, stat, text, hex, checksum, ziplist, apkinfo, raw`
`mkdir, mkfile, rename, delete, copy, move, chmod, compress, extract, save, upload_b64, zip_one`

## Project layout

```
filemanager.py  # the whole app (backend + embedded web UI)
README.md
requirements.txt  # empty — stdlib only
LICENSE
```

## Security note

Binds to localhost by default. `--host 0.0.0.0` exposes your files to the local network — use only on trusted networks.

## License

MIT — see LICENSE.
