# FMX — All-in-One File Manager

Native Android app (Kotlin + Jetpack Compose, Material 3) **plus** a
single-file Python web companion. No ads, no accounts.

Functionality merged from four great managers:

| From | What FMX takes from it |
|---|---|
| **ZArchiver** | ZIP / TAR / 7z browse + extract, password (AES) ZIPs, split/merge parts, single-entry extract, app backup |
| **MT Manager** | Dual-pane browsing, APK viewer (package, version, permissions, DEX), hex viewer **+ byte editor**, batch rename, text editor with find/replace |
| **MiXplorer** | Unlimited tabs, dual panels, recycle-bin Trash, app manager (backup/uninstall), Wi-Fi share server, storage analyzer + Home dashboard, grid/list views, AES file vault, checksum compare, font viewer, content search |
| **Material Files** | Clean Material 3 UI (incl. true-black + dynamic colors), breadcrumbs, Linux-aware details (symlinks, permissions), copy-path, drawer navigation |

## Android app (native)

```
android/            # Kotlin + Compose, Material 3, minSdk 26
```

Every push to `main` builds a debug APK via GitHub Actions:

1. Repo on GitHub → **Actions** → latest **Build APK** run.
2. Download **fmx-debug-apk**, install, grant **All files access**.

Screens: **Files** (tabs, dual-pane, multi-select, bottom sheets) ·
**Home** (storage + category dashboard) · **Apps** (backup/uninstall/inspect) ·
**Trash** (30-day recycle bin) · **Tools** (Wi-Fi share, storage analyzer) ·
viewers (image/video/audio/PDF/font/text/hex) · editors · archive + APK tools.

## Python web companion

`filemanager.py` is the original stdlib-only server version (Termux/PC),
also PWA-installable from the browser:

```bash
python3 filemanager.py --port 8080
# open http://127.0.0.1:8080
```

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

**Native APK (recommended).** Actions → latest **Build APK** →
**fmx-debug-apk** → install → grant **All files access**.

**Browser PWA (no build).** Run the Python server, open the URL in
Chrome → *Menu → Add to Home screen / Install app*.

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
android/          # native Kotlin app (Compose/Material3)
filemanager.py    # Python web companion (stdlib only, PWA)
README.md
requirements.txt  # empty — stdlib only
LICENSE
```

## Security note

Binds to localhost by default. `--host 0.0.0.0` exposes your files to the local network — use only on trusted networks.

## License

MIT — see LICENSE.
