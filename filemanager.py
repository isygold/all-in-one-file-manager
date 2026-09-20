#!/usr/bin/env python3
"""
FMX - All-in-One File Manager
=============================
Single-file, stdlib-only, mobile-friendly file manager for Termux / Android / Linux.

Features (MT Manager + ZArchiver inspired):
- browse / copy / cut / paste / rename / delete / mkdir / mkfile / chmod
- search by name (recursive), sort by name/size/mtime/type, hidden toggle
- previews: image, video, audio, pdf, text/code
- text editor + hex viewer + checksum (md5/sha1/sha256)
- archives: create zip/tar.gz, extract zip/tar, browse inside zip/apk/jar (ZArchiver-style)
- apk inspector: dex/so/res listing, aapt badging if available (MT-style)
- tabs, history, bookmarks, dual-pane (wide screens), dark mode, multi-select
- upload (base64 JSON, no deps) + download/raw streaming
- upload/download friendly mobile web UI served from this one file

Run:
    python3 filemanager.py --port 8080
    # then open http://127.0.0.1:8080 in your phone browser

Only Python stdlib is used. No pip install needed.
"""
import argparse
import base64
import hashlib
import html as htmlmod
import json
import mimetypes
import os
import shutil
import stat as statmod
import subprocess
import sys
import tarfile
import time
import urllib.parse
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

APP_NAME = "FMX File Manager"
VERSION = "1.0.0"
HOME = os.path.expanduser("~")

# ---------------------------------------------------------------- helpers

def resolve(p):
    """Normalize to absolute path. Relative -> relative to HOME."""
    if not p:
        return HOME
    p = os.path.expanduser(p)
    if not os.path.isabs(p):
        p = os.path.join(HOME, p)
    return os.path.abspath(p)


def safe_stat(path):
    st = os.lstat(path)
    return {
        "name": os.path.basename(path) or path,
        "path": path,
        "is_dir": os.path.isdir(path) and not os.path.islink(path),
        "is_link": os.path.islink(path),
        "size": st.st_size if not os.path.isdir(path) else 0,
        "mtime": int(st.st_mtime),
        "mode": oct(statmod.S_IMODE(st.st_mode)),
        "ext": os.path.splitext(path)[1].lower().lstrip(".")[:10],
    }


def list_dir(path, sort="name", desc=False, show_hidden=False):
    path = resolve(path)
    entries = []
    with os.scandir(path) as it:
        for e in it:
            if not show_hidden and e.name.startswith("."):
                continue
            try:
                is_dir = e.is_dir(follow_symlinks=False)
                s = e.stat(follow_symlinks=False)
                entries.append({
                    "name": e.name,
                    "path": os.path.join(path, e.name),
                    "is_dir": is_dir,
                    "size": 0 if is_dir else s.st_size,
                    "mtime": int(s.st_mtime),
                    "mode": oct(statmod.S_IMODE(s.st_mode)),
                    "ext": "" if is_dir else os.path.splitext(e.name)[1].lower().lstrip(".")[:10],
                })
            except (OSError, PermissionError):
                entries.append({
                    "name": e.name, "path": os.path.join(path, e.name),
                    "is_dir": False, "size": 0, "mtime": 0,
                    "mode": "---", "ext": "", "error": True,
                })
    key = {"name": lambda x: x["name"].lower(),
           "size": lambda x: (x["is_dir"], x["size"]),
           "mtime": lambda x: x["mtime"],
           "type": lambda x: (x["is_dir"], x["ext"], x["name"].lower())}.get(sort, lambda x: x["name"].lower())
    # dirs first always (like MT / ZArchiver)
    entries.sort(key=lambda x: (not x["is_dir"], key(x)), reverse=desc)
    return entries


def search_files(base, query, max_results=300):
    base = resolve(base)
    q = query.lower()
    out = []
    for root, dirs, files in os.walk(base, topdown=True, onerror=lambda e: None, followlinks=False):
        # prune hidden + huge dirs
        dirs[:] = [d for d in dirs if not d.startswith(".")][:200]
        # check dir names too
        for name in dirs + files:
            if q in name.lower():
                full = os.path.join(root, name)
                try:
                    out.append(safe_stat(full))
                except OSError:
                    pass
                if len(out) >= max_results:
                    return out
        if len(out) >= max_results:
            break
        if len(root.split(os.sep)) > 25:  # depth guard
            dirs[:] = []
    return out


def checksum(path, algo="sha256"):
    h = hashlib.new(algo)
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def hexdump(path, offset=0, length=4096):
    with open(path, "rb") as f:
        f.seek(offset)
        data = f.read(length)
    lines = []
    for i in range(0, len(data), 16):
        chunk = data[i:i + 16]
        hexs = " ".join(f"{b:02x}" for b in chunk)
        asc = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
        lines.append(f"{offset+i:08x}  {hexs:<48}  |{asc}|")
    return {"offset": offset, "length": len(data), "lines": lines,
            "size": os.path.getsize(path)}


def zip_list(path):
    with zipfile.ZipFile(path, "r") as z:
        infos = []
        for i in z.infolist():
            infos.append({"name": i.filename, "size": i.file_size,
                          "comp": i.compress_size, "is_dir": i.is_dir(),
                          "mtime": "%04d-%02d-%02d" % i.date_time[:3]})
        return {"count": len(infos), "files": infos[:2000], "truncated": len(infos) > 2000}


def apk_info(path):
    info = {"file": os.path.basename(path), "size": os.path.getsize(path)}
    try:
        info["md5"] = checksum(path, "md5")
    except OSError:
        pass
    with zipfile.ZipFile(path, "r") as z:
        names = z.namelist()
        info["entries"] = len(names)
        info["dex"] = sorted([n for n in names if n.endswith(".dex")])
        info["so"] = sorted([n for n in names if n.endswith(".so")])[:50]
        info["has_manifest"] = "AndroidManifest.xml" in names
        info["has_resources"] = "resources.arsc" in names
        # try aapt if present
        for tool in ("aapt", "aapt2"):
            if shutil.which(tool):
                try:
                    r = subprocess.run([tool, "dump", "badging", path],
                                       capture_output=True, text=True, timeout=15)
                    if r.returncode == 0:
                        info["aapt"] = r.stdout[:3000]
                        break
                except Exception:
                    pass
        # fallback: binary manifest often contains package string in cleartext
        if "AndroidManifest.xml" in names:
            try:
                raw = z.read("AndroidManifest.xml")[:20000]
                # crude printable-string scan for dotted package-like token
                import re
                strs = re.findall(rb"[A-Za-z][A-Za-z0-9_]{2,}(?:\.[A-Za-z0-9_]{2,})+", raw)
                cands = sorted({s.decode() for s in strs if s.count(b".") >= 2}, key=len)
                if cands:
                    info["maybe_package"] = cands[:5]
            except Exception:
                pass
    return info


def do_compress(srcs, out, fmt="zip"):
    out = resolve(out)
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    if fmt == "zip":
        if not out.endswith(".zip"):
            out += ".zip"
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
            for s in srcs:
                s = resolve(s)
                if os.path.isdir(s):
                    for root, _, files in os.walk(s):
                        for f in files:
                            full = os.path.join(root, f)
                            z.write(full, os.path.relpath(full, os.path.dirname(s)))
                else:
                    z.write(s, os.path.basename(s))
    else:
        mode = {"tar": "w", "tar.gz": "w:gz", "tgz": "w:gz",
                "tar.bz2": "w:bz2", "tar.xz": "w:xz"}.get(fmt, "w:gz")
        if fmt == "tar" and not out.endswith(".tar"):
            out += ".tar"
        with tarfile.open(out, mode) as t:
            for s in srcs:
                s = resolve(s)
                t.add(s, arcname=os.path.basename(s))
    return out


def do_extract(archive, dst):
    archive = resolve(archive)
    dst = resolve(dst)
    os.makedirs(dst, exist_ok=True)
    if zipfile.is_zipfile(archive):
        with zipfile.ZipFile(archive, "r") as z:
            z.extractall(dst)
            return {"type": "zip", "count": len(z.namelist()), "dst": dst}
    else:
        with tarfile.open(archive, "r:*") as t:
            # py3.14: filter for safe extraction
            try:
                t.extractall(dst, filter="data")
            except TypeError:
                t.extractall(dst)
            return {"type": "tar", "count": len(t.getnames()), "dst": dst}


def copy_move(srcs, dst, move=False):
    dst = resolve(dst)
    os.makedirs(dst, exist_ok=True)
    done = []
    for s in srcs:
        s = resolve(s)
        base = os.path.basename(s.rstrip("/"))
        target = os.path.join(dst, base)
        if os.path.abspath(s) == os.path.abspath(target):
            continue
        if move:
            shutil.move(s, target)
        else:
            if os.path.isdir(s) and not os.path.islink(s):
                shutil.copytree(s, target, dirs_exist_ok=True)
            else:
                shutil.copy2(s, target)
        done.append(target)
    return done


def fmt_size(n):
    for u in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024:
            return f"{n:.0f}{u}" if u == "B" else f"{n:.1f}{u}"
        n /= 1024.0
    return f"{n:.1f}PB"

# ---------------------------------------------------------------- HTTP

class Handler(BaseHTTPRequestHandler):
    server_version = "FMX/1.0"

    def log_message(self, *a):
        pass  # quiet; change to super() for debug

    # -- helpers
    def _json(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self):
        n = int(self.headers.get("Content-Length", 0) or 0)
        if n <= 0:
            return {}
        raw = self.rfile.read(n)
        try:
            return json.loads(raw.decode())
        except Exception:
            return {}

    def _q(self):
        return dict(urllib.parse.parse_qsl(urllib.parse.urlparse(self.path).query))

    # -- GET
    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        route = url.path
        q = dict(urllib.parse.parse_qsl(url.query))
        try:
            if route in ("/", "/index.html"):
                body = HTML_PAGE.encode()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            if route == "/manifest.webmanifest":
                return self._json(PWA_MANIFEST)
            if route == "/sw.js":
                body = SW_JS.encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/javascript")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            if route in ("/icon-192.png", "/icon-512.png"):
                size = 192 if "192" in route else 512
                body = _solid_png(size)
                self.send_response(200)
                self.send_header("Content-Type", "image/png")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            if route == "/api/roots":
                roots = []
                cands = [HOME,
                         os.path.join(HOME, "storage", "shared"),
                         "/sdcard", "/storage/emulated/0", "/"]
                seen = set()
                for c in cands:
                    if c in seen:
                        continue
                    seen.add(c)
                    if os.path.isdir(c):
                        roots.append({"name": {"%s" % HOME: "Home"}.get(c, c),
                                      "path": os.path.abspath(c)})
                return self._json({"roots": roots, "home": HOME, "version": VERSION})
            if route == "/api/list":
                p = resolve(q.get("path", HOME))
                entries = list_dir(p, q.get("sort", "name"),
                                   q.get("desc", "0") == "1",
                                   q.get("hidden", "0") == "1")
                return self._json({"path": p, "parent": os.path.dirname(p),
                                   "entries": entries, "total": len(entries)})
            if route == "/api/search":
                base = resolve(q.get("base", HOME))
                res = search_files(base, q.get("q", ""), int(q.get("max", "300") or 300))
                return self._json({"base": base, "q": q.get("q", ""), "results": res})
            if route == "/api/stat":
                p = resolve(q.get("path", ""))
                st = safe_stat(p)
                st["human"] = fmt_size(st["size"]) if not st["is_dir"] else "-"
                if os.path.isdir(p) and not os.path.islink(p):
                    try:
                        st["children"] = len(os.listdir(p))
                    except OSError:
                        st["children"] = -1
                return self._json(st)
            if route == "/api/text":
                p = resolve(q.get("path", ""))
                mx = int(q.get("max", "200000") or 200000)
                with open(p, "rb") as f:
                    raw = f.read(mx + 1)
                trunc = len(raw) > mx
                return self._json({"path": p, "content": raw[:mx].decode("utf-8", "replace"),
                                   "truncated": trunc, "size": os.path.getsize(p)})
            if route == "/api/hex":
                p = resolve(q.get("path", ""))
                d = hexdump(p, int(q.get("offset", "0") or 0), int(q.get("len", "4096") or 4096))
                return self._json(d)
            if route == "/api/checksum":
                p = resolve(q.get("path", ""))
                algo = q.get("algo", "sha256")
                return self._json({"path": p, "algo": algo, "hex": checksum(p, algo)})
            if route == "/api/ziplist":
                return self._json(zip_list(resolve(q.get("path", ""))))
            if route == "/api/apkinfo":
                return self._json(apk_info(resolve(q.get("path", ""))))
            if route == "/api/raw":
                p = resolve(q.get("path", ""))
                if os.path.isdir(p):
                    return self._json({"error": "is a directory"}, 400)
                mime, _ = mimetypes.guess_type(p)
                mime = mime or "application/octet-stream"
                size = os.path.getsize(p)
                self.send_response(200)
                self.send_header("Content-Type", mime)
                self.send_header("Content-Length", str(size))
                self.send_header("Content-Disposition",
                                 f'inline; filename="{htmlmod.escape(os.path.basename(p))}"')
                self.end_headers()
                with open(p, "rb") as f:
                    shutil.copyfileobj(f, self.wfile, length=1024 * 64)
                return
            return self._json({"error": "unknown route"}, 404)
        except FileNotFoundError as e:
            return self._json({"error": f"not found: {e}"}, 404)
        except PermissionError:
            return self._json({"error": "permission denied"}, 403)
        except Exception as e:
            return self._json({"error": f"{type(e).__name__}: {e}"}, 500)

    # -- POST
    def do_POST(self):
        route = urllib.parse.urlparse(self.path).path
        try:
            d = self._read_json()
            R = resolve
            if route == "/api/mkdir":
                p = os.path.join(R(d.get("dir", HOME)), d.get("name", "New Folder"))
                os.makedirs(p, exist_ok=False)
                return self._json({"ok": True, "path": p})
            if route == "/api/mkfile":
                p = os.path.join(R(d.get("dir", HOME)), d.get("name", "new.txt"))
                if os.path.exists(p):
                    return self._json({"error": "exists"}, 400)
                open(p, "w").close()
                return self._json({"ok": True, "path": p})
            if route == "/api/rename":
                src = R(d.get("path", ""))
                np = os.path.join(os.path.dirname(src), d.get("new_name", ""))
                os.rename(src, np)
                return self._json({"ok": True, "path": np})
            if route == "/api/delete":
                for s in d.get("paths", []):
                    s = R(s)
                    if os.path.isdir(s) and not os.path.islink(s):
                        shutil.rmtree(s)
                    else:
                        os.remove(s)
                return self._json({"ok": True})
            if route in ("/api/copy", "/api/move"):
                done = copy_move(d.get("srcs", []), d.get("dst", HOME),
                                 move=(route == "/api/move"))
                return self._json({"ok": True, "done": done})
            if route == "/api/chmod":
                mode = int(str(d.get("mode", "644")), 8)
                for s in d.get("paths", []):
                    os.chmod(R(s), mode)
                return self._json({"ok": True})
            if route == "/api/compress":
                out = do_compress(d.get("srcs", []), d.get("out", "/tmp/out.zip"),
                                  d.get("fmt", "zip"))
                return self._json({"ok": True, "out": out})
            if route == "/api/extract":
                r = do_extract(d.get("archive", ""), d.get("dst", HOME))
                return self._json({"ok": True, **r})
            if route == "/api/save":
                p = R(d.get("path", ""))
                with open(p, "w", encoding="utf-8") as f:
                    f.write(d.get("content", ""))
                return self._json({"ok": True, "size": os.path.getsize(p)})
            if route == "/api/upload_b64":
                folder = R(d.get("dir", HOME))
                os.makedirs(folder, exist_ok=True)
                p = os.path.join(folder, os.path.basename(d.get("name", "upload.bin")))
                with open(p, "wb") as f:
                    f.write(base64.b64decode(d.get("b64", "")))
                return self._json({"ok": True, "path": p})
            if route == "/api/zip_one":
                arch, inner, dst = R(d.get("archive", "")), d.get("inner", ""), R(d.get("dst", HOME))
                os.makedirs(dst, exist_ok=True)
                with zipfile.ZipFile(arch, "r") as z:
                    z.extract(inner, dst)
                return self._json({"ok": True, "dst": os.path.join(dst, inner)})
            return self._json({"error": "unknown route"}, 404)
        except FileExistsError:
            return self._json({"error": "already exists"}, 400)
        except PermissionError:
            return self._json({"error": "permission denied"}, 403)
        except Exception as e:
            return self._json({"error": f"{type(e).__name__}: {e}"}, 500)


# ---------------------------------------------------------------- PWA (installable app)
PWA_MANIFEST = {
    "name": "FMX File Manager",
    "short_name": "FMX",
    "description": "All-in-one file manager (MT + ZArchiver style)",
    "start_url": "/",
    "scope": "/",
    "display": "standalone",
    "orientation": "any",
    "background_color": "#0f172a",
    "theme_color": "#0f172a",
    "icons": [
        {"src": "/icon-192.png", "sizes": "192x192", "type": "image/png"},
        {"src": "/icon-512.png", "sizes": "512x512", "type": "image/png"},
    ],
}

SW_JS = """self.addEventListener('install',e=>{self.skipWaiting()});
self.addEventListener('activate',e=>{e.waitUntil(clients.claim())});
self.addEventListener('fetch',e=>{
  const u=new URL(e.request.url);
  if(u.pathname.startsWith('/api/')) return; // never cache API
  if(e.request.method!=='GET') return;
  e.respondWith(caches.open('fmx-v1').then(async c=>{
    try{const r=await fetch(e.request);c.put(e.request,r.clone());return r}
    catch(err){const hit=await c.match(e.request);return hit||Response.error()}
  }));
});
"""


def _solid_png(size=192, rgb=(56, 189, 248)):
    """Minimal solid PNG (stdlib only) for app icons. Dark bg + lighter center."""
    import struct
    import zlib
    bg = (15, 23, 42)
    px = bytearray()
    for y in range(size):
        px.append(0)  # filter byte
        for x in range(size):
            # rounded-ish lighter square in the middle (fake "F" block look)
            m = size // 4
            if m < x < size - m and m < y < size - m:
                r, g, b = rgb
            else:
                r, g, b = bg
            px += bytes((r, g, b))
    raw = bytes(px)
    def chunk(t, d):
        c = t + d
        return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


# ---------------------------------------------------------------- Web UI (mobile-first, single file)
HTML_PAGE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<meta name="theme-color" content="#0f172a">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<link rel="manifest" href="/manifest.webmanifest">
<link rel="icon" href="/icon-192.png">
<link rel="apple-touch-icon" href="/icon-192.png">
<title>FMX - All-in-One File Manager</title>
<style>
:root{--bg:#0f172a;--card:#1e293b;--line:#334155;--tx:#e2e8f0;--mut:#94a3b8;--ac:#38bdf8;--ok:#22c55e;--warn:#f59e0b;--bad:#ef4444}
body.light{--bg:#f1f5f9;--card:#ffffff;--line:#e2e8f0;--tx:#0f172a;--mut:#64748b;--ac:#0284c7}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;background:var(--bg);color:var(--tx);font-family:system-ui,Roboto,Arial,sans-serif;padding-bottom:170px}
header{position:sticky;top:0;z-index:20;background:var(--card);border-bottom:1px solid var(--line);padding:10px 12px}
.top{display:flex;gap:8px;align-items:center}
.logo{font-weight:800;font-size:18px}.logo span{color:var(--ac)}
#q{flex:1;background:var(--bg);border:1px solid var(--line);color:var(--tx);border-radius:10px;padding:10px}
button{background:var(--ac);border:0;color:#04121f;font-weight:700;border-radius:10px;padding:10px 12px;font-size:14px}
button.ghost{background:transparent;border:1px solid var(--line);color:var(--tx)}
button.danger{background:var(--bad);color:#fff}
button.small{padding:6px 8px;font-size:12px}
.roots{display:flex;gap:6px;margin-top:8px;overflow-x:auto}
.tabs{display:flex;gap:6px;margin-top:8px;overflow-x:auto}
.tab{border:1px solid var(--line);border-radius:999px;padding:6px 10px;font-size:12px;white-space:nowrap;background:var(--bg)}
.tab.on{background:var(--ac);color:#04121f;border-color:var(--ac);font-weight:800}
.crumb{display:flex;gap:4px;align-items:center;margin-top:8px;overflow-x:auto;font-size:13px;color:var(--mut)}
.crumb b{color:var(--tx)}
.controls{display:flex;gap:6px;margin-top:8px}
.controls select{background:var(--bg);color:var(--tx);border:1px solid var(--line);border-radius:10px;padding:8px}
#list{padding:8px}
.row{display:flex;gap:10px;align-items:center;background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px;margin:8px 0;min-height:56px}
.row.sel{outline:2px solid var(--ac)}
.ic{font-size:22px;width:30px;text-align:center}
.nm{flex:1;min-width:0}.nm b{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:14px}
.nm small{color:var(--mut);font-size:11px}
.chk{width:26px;height:26px}
#dock{position:fixed;left:0;right:0;bottom:0;background:var(--card);border-top:1px solid var(--line);padding:10px;z-index:30}
#selbar{font-size:12px;color:var(--mut);margin-bottom:8px;display:flex;justify-content:space-between}
.actions{display:grid;grid-template-columns:repeat(4,1fr);gap:6px}
#fab{position:fixed;right:14px;bottom:150px;z-index:31;display:flex;flex-direction:column;gap:8px}
#fab button{border-radius:999px;width:52px;height:52px;font-size:22px;box-shadow:0 4px 14px rgba(0,0,0,.4)}
.modal{position:fixed;inset:0;background:rgba(0,0,0,.6);display:none;z-index:50;padding:14px}
.modal.open{display:block}
.sheet{background:var(--card);border:1px solid var(--line);border-radius:14px;max-width:720px;margin:4vh auto;max-height:88vh;display:flex;flex-direction:column;overflow:hidden}
.sheet h3{margin:0;padding:14px;border-bottom:1px solid var(--line)}
.body{padding:14px;overflow:auto}
.body input,.body textarea,.body select{width:100%;background:var(--bg);border:1px solid var(--line);color:var(--tx);border-radius:10px;padding:10px;margin:6px 0}
.body textarea{font-family:monospace;min-height:40vh}
.foot{padding:12px;display:flex;gap:8px;border-top:1px solid var(--line)}
pre{background:#000;color:#a5f3fc;padding:10px;border-radius:10px;overflow:auto;font-size:12px}
img.pv,video.pv{max-width:100%;border-radius:10px}
#toast{position:fixed;top:12px;left:50%;transform:translateX(-50%);background:#000;color:#fff;padding:10px 14px;border-radius:999px;z-index:99;display:none;font-size:13px}
.dual{display:grid;grid-template-columns:1fr 1fr;gap:8px}
@media(max-width:700px){.dual{grid-template-columns:1fr}}
.kv{font-size:13px}.kv div{padding:6px 0;border-bottom:1px dashed var(--line)}
</style>
</head>
<body>
<div id="toast"></div>
<header>
<div class="top"><div class="logo">FMX <span>Manager</span></div>
<input id="q" placeholder="Search in folder... (Enter)">
<button class="ghost small" onclick="toggleTheme()">&#9790;</button>
<button class="ghost small" onclick="openHelp()">?</button></div>
<div class="roots" id="roots"></div>
<div class="tabs" id="tabs"></div>
<div class="crumb" id="crumb"></div>
<div class="controls">
<select id="sort" onchange="reload()"><option value="name">Name</option><option value="size">Size</option><option value="mtime">Date</option><option value="type">Type</option></select>
<select id="order" onchange="reload()"><option value="0">Asc</option><option value="1">Desc</option></select>
<button class="ghost small" id="hidBtn" onclick="toggleHidden()">hidden:off</button>
<button class="ghost small" onclick="goUp()">..</button>
<button class="ghost small" onclick="addTab()">+tab</button>
</div>
</header>
<div id="list"></div>
<div id="fab"><button onclick="fabMenu()">+</button></div>
<div id="dock"><div id="selbar"><span id="selinfo">no selection</span><span id="clipinfo"></span></div>
<div class="actions">
<button class="ghost" onclick="doCopy()">Copy</button>
<button class="ghost" onclick="doCut()">Cut</button>
<button class="ghost" onclick="doPaste()">Paste</button>
<button class="danger" onclick="doDelete()">Del</button>
<button class="ghost" onclick="doRename()">Ren</button>
<button class="ghost" onclick="doCompress()">Zip</button>
<button class="ghost" onclick="doChmod()">777</button>
<button class="ghost" onclick="showProps()">Info</button>
</div></div>
<div class="modal" id="m"><div class="sheet"><h3 id="mt"></h3><div class="body" id="mb"></div><div class="foot" id="mf"></div></div></div>
<script>
let S={tabs:[{path:'',hist:[],hi:-1}],ai:0,sel:new Set(),clip:null,hidden:false,roots:[],home:''};
const $=id=>document.getElementById(id);
function toast(t){const e=$('toast');e.textContent=t;e.style.display='block';clearTimeout(e._t);e._t=setTimeout(()=>e.style.display='none',2200)}
async function api(p,o){const r=await fetch(p,o);const j=await r.json().catch(()=>({error:'bad json'}));if(!r.ok)throw new Error(j.error||r.status);if(j.error)throw new Error(j.error);return j}
function cur(){return S.tabs[S.ai]}
async function init(){if(localStorage.fmx_dark==='1')document.body.classList.add('light');
const r=await api('/api/roots');S.roots=r.roots;S.home=r.home;
$('roots').innerHTML=r.roots.map(x=>`<button class="ghost small" onclick="nav('${x.path}')">${x.name}</button>`).join('');
if(!cur().path)cur().path=r.home;renderTabs();await reload();}
function renderTabs(){$('tabs').innerHTML=S.tabs.map((t,i)=>`<div class="tab ${i===S.ai?'on':''}" onclick="swTab(${i})">${i+1}:${esc(short(t.path))} <span onclick="event.stopPropagation();closeTab(${i})">x</span></div>`).join('')}
function short(p){if(!p)return '/';const h=S.home;if(p===h)return 'home';if(p.startsWith(h))return '~'+p.slice(h.length);return p.length>24?'...'+p.slice(-21):p}
function swTab(i){S.ai=i;S.sel.clear();renderTabs();reload()}
function addTab(){S.tabs.push({path:cur().path,hist:[],hi:-1});S.ai=S.tabs.length-1;renderTabs();reload()}
function closeTab(i){if(S.tabs.length===1)return;S.tabs.splice(i,1);if(S.ai>=S.tabs.length)S.ai=0;renderTabs();reload()}
async function nav(p){const t=cur();t.hist=t.hist.slice(0,t.hi+1);t.hist.push(p);t.hi++;t.path=p;S.sel.clear();renderTabs();await reload()}
function goUp(){fetch('/api/list?path='+encodeURIComponent(cur().path)).then(r=>r.json()).then(j=>{if(j.parent&&j.parent!==j.path)nav(j.parent)})}
function back(){const t=cur();if(t.hi>0){t.hi--;t.path=t.hist[t.hi];reload();renderTabs()}}
function esc(s){return (s||'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}
function icon(e){if(e.is_dir)return '&#128193;';const x=(e.ext||'').toLowerCase();
if(['png','jpg','jpeg','gif','webp','bmp','svg'].includes(x))return '&#128444;';
if(['mp4','mkv','webm','3gp'].includes(x))return '&#127916;';
if(['mp3','ogg','m4a','flac','wav'].includes(x))return '&#127925;';
if(['zip','7z','rar','tar','gz','tgz','apk','jar'].includes(x))return '&#128230;';
if(['pdf'].includes(x))return '&#128209;';
if(['txt','md','py','js','html','css','json','java','c','cpp','sh','xml','yml'].includes(x))return '&#128196;';
return '&#128459;'}
function fmt(n){if(n<1024)return n+'B';n/=1024;if(n<1024)return n.toFixed(1)+'KB';n/=1024;if(n<1024)return n.toFixed(1)+'MB';n/=1024;return n.toFixed(1)+'GB'}
async function reload(){const t=cur();const s=$('sort').value,o=$('order').value;
const j=await api('/api/list?path='+encodeURIComponent(t.path)+'&sort='+s+'&desc='+o+'&hidden='+(S.hidden?1:0));
t.path=j.path;$('crumb').innerHTML='<span onclick="back()">&#8592;</span> <b>'+esc(j.path)+'</b> ('+j.total+')';
const L=$('list');L.innerHTML='';
j.entries.forEach(e=>{const d=document.createElement('div');d.className='row'+(S.sel.has(e.path)?' sel':'');
d.innerHTML=`<input type="checkbox" class="chk" ${S.sel.has(e.path)?'checked':''}><div class="ic">${icon(e)}</div><div class="nm"><b>${esc(e.name)}</b><small>${e.is_dir?'folder':fmt(e.size)+' - '+new Date(e.mtime*1000).toLocaleString()+' - '+e.mode}</small></div>`;
d.querySelector('input').onchange=ev=>{ev.stopPropagation();ev.target.checked?S.sel.add(e.path):S.sel.delete(e.path);updSel();d.classList.toggle('sel',ev.target.checked)};
d.onclick=()=>openEntry(e);L.appendChild(d)});updSel()}
function updSel(){$('selinfo').textContent=S.sel.size?S.sel.size+' selected':'no selection';
$('clipinfo').textContent=S.clip?((S.clip.mode==='cut'?'cut:':'copy:')+S.clip.paths.length):'';
$('hidBtn').textContent='hidden:'+(S.hidden?'on':'off')}
function toggleHidden(){S.hidden=!S.hidden;reload();updSel()}
function toggleTheme(){document.body.classList.toggle('light');localStorage.fmx_dark=document.body.classList.contains('light')?'1':'0'}
async function openEntry(e){if(e.is_dir){nav(e.path);return}
const x=e.ext;const img=['png','jpg','jpeg','gif','webp','bmp','svg'],vid=['mp4','webm','3gp','mkv'],aud=['mp3','ogg','m4a','wav','flac'];
if(img.includes(x))return preview(`<img class="pv" src="/api/raw?path=${encodeURIComponent(e.path)}">`,e.name);
if(vid.includes(x))return preview(`<video class="pv" controls src="/api/raw?path=${encodeURIComponent(e.path)}"></video>`,e.name);
if(aud.includes(x))return preview(`<audio controls src="/api/raw?path=${encodeURIComponent(e.path)}" style="width:100%"></audio>`,e.name);
if(x==='pdf')return preview(`<iframe src="/api/raw?path=${encodeURIComponent(e.path)}" style="width:100%;height:60vh;border:0"></iframe>`,e.name);
if(['zip','apk','jar'].includes(x))return viewZip(e);
if(x==='apk')return viewZip(e);
return openEditor(e.path)}
function preview(h,title){openModal(title,h,[['Close',closeModal]])}
function openModal(t,htmlBtns,btns){$('mt').textContent=t;$('mb').innerHTML=htmlBtns;$('mf').innerHTML='';
(btns||[]).forEach(([l,f,cls])=>{const b=document.createElement('button');b.textContent=l;if(cls)b.className=cls;b.onclick=f;$('mf').appendChild(b)});
$('m').classList.add('open')}
function closeModal(){$('m').classList.remove('open')}
$('m').addEventListener('click',e=>{if(e.target.id==='m')closeModal()});
async function openEditor(path){try{const j=await api('/api/text?path='+encodeURIComponent(path));
openModal('Edit: '+path.split('/').pop(),`<textarea id="ed">${esc(j.content)}</textarea>${j.truncated?'<small>truncated at 200KB</small>':''}`,
[['Save',async()=>{await api('/api/save',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({path,content:$('ed').value})});toast('saved');closeModal()},''],['Hex',()=>viewHex(path),'ghost'],['Checksum',()=>viewSum(path),'ghost'],['Close',closeModal,'ghost']])}catch(e){toast(e.message)}}
async function viewHex(path){const j=await api('/api/hex?path='+encodeURIComponent(path));openModal('Hex: '+path.split('/').pop(),`<pre>${esc(j.lines.join('\\n'))}</pre><small>${j.size} bytes</small>`,[['Close',closeModal]])}
async function viewSum(path){const a=['md5','sha1','sha256'];let h='';for(const x of a){try{const j=await api('/api/checksum?path='+encodeURIComponent(path)+'&algo='+x);h+=`<div><b>${x}</b>: ${j.hex}</div>`}catch(e){h+=`<div>${x}: err</div>`}}
openModal('Checksum',`<div class="kv">${h}</div>`,[['Close',closeModal]])}
async function viewZip(e){try{const j=await api('/api/ziplist?path='+encodeURIComponent(e.path));
const rows=j.files.slice(0,300).map(f=>`<div><small>${f.is_dir?'&#128193;':'&#128459;'} ${esc(f.name)} (${fmt(f.size)})</small></div>`).join('');
openModal((e.ext==='apk'?'APK/ZIP: ':'ZIP: ')+e.name,`<div class="kv"><div>entries: ${j.count}</div>${rows}</div>${e.ext==='apk'?'<button class="ghost small" id="apkbtn">APK info</button>':''}<br><button class="ghost small" id="extbtn">Extract here</button>`,
[['Close',closeModal]]);const eb=$('extbtn');if(eb)eb.onclick=()=>doExtractApi(e.path,cur().path);
const ab=$('apkbtn');if(ab)ab.onclick=()=>viewApk(e.path)}catch(err){toast(err.message)}}
async function viewApk(path){try{const j=await api('/api/apkinfo?path='+encodeURIComponent(path));
openModal('APK info',`<div class="kv"><div><b>${esc(j.file)}</b> ${fmt(j.size)}</div><div>md5: ${j.md5||'-'}</div><div>dex: ${(j.dex||[]).join(', ')}</div><div>manifest: ${j.has_manifest} arsc: ${j.has_resources}</div><div>maybe_pkg: ${(j.maybe_package||[]).join(', ')}</div><pre>${esc((j.aapt||'aapt not found - install aapt for full badging').slice(0,2000))}</pre></div>`,[['Close',closeModal]])}catch(e){toast(e.message)}}
async function doExtractApi(arch,dst){try{await api('/api/extract',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({archive:arch,dst})});toast('extracted');closeModal();reload()}catch(e){toast(e.message)}}
let clipMode=null;
function doCopy(){if(!S.sel.size)return toast('select files');S.clip={mode:'copy',paths:[...S.sel]};updSel();toast('copied '+S.clip.paths.length)}
function doCut(){if(!S.sel.size)return toast('select files');S.clip={mode:'cut',paths:[...S.sel]};updSel();toast('cut '+S.clip.paths.length)}
async function doPaste(){if(!S.clip)return toast('clipboard empty');try{await api(S.clip.mode==='cut'?'/api/move':'/api/copy',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({srcs:S.clip.paths,dst:cur().path})});if(S.clip.mode==='cut')S.clip=null;S.sel.clear();reload()}catch(e){toast(e.message)}}
async function doDelete(){if(!S.sel.size)return toast('select files');if(!confirm('delete '+S.sel.size+'?'))return;
try{await api('/api/delete',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paths:[...S.sel]})});S.sel.clear();reload()}catch(e){toast(e.message)}}
async function doRename(){if(S.sel.size!==1)return toast('select 1 file');const old=[...S.sel][0];const nn=prompt('new name',old.split('/').pop());if(!nn)return;
try{await api('/api/rename',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({path:old,new_name:nn})});S.sel.clear();reload()}catch(e){toast(e.message)}}
async function doChmod(){if(!S.sel.size)return toast('select files');const m=prompt('octal mode (644/755/777)', '755');if(!m)return;
try{await api('/api/chmod',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paths:[...S.sel],mode:m})});toast('chmod ok');reload()}catch(e){toast(e.message)}}
async function doCompress(){if(!S.sel.size)return toast('select files');const n=prompt('archive name','archive.zip');if(!n)return;
const fmt=n.endsWith('.tar.gz')||n.endsWith('.tgz')?'tar.gz':n.endsWith('.zip')?'zip':'zip';
try{await api('/api/compress',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({srcs:[...S.sel],out:cur().path+'/'+n,fmt})});toast('compressed');reload()}catch(e){toast(e.message)}}
async function showProps(){if(S.sel.size!==1)return toast('select 1 file');try{const p=[...S.sel][0];const j=await api('/api/stat?path='+encodeURIComponent(p));
openModal('Properties',`<div class="kv"><div><b>${esc(j.name)}</b></div><div>${esc(j.path)}</div><div>size: ${j.human} (${j.size})</div><div>mode: ${j.mode} mtime: ${new Date(j.mtime*1000).toLocaleString()}</div>${j.children!==undefined?'<div>children: '+j.children+'</div>':''}</div>`,
[['Checksum',()=>viewSum(p),'ghost'],['Close',closeModal,'ghost']])}catch(e){toast(e.message)}}
function fabMenu(){openModal('New / Upload',`<input id="nn" placeholder="folder or file name"><br>
<button class="ghost small" onclick="mk('dir')">New folder</button> <button class="ghost small" onclick="mk('file')">New file</button><br><br>
<input type="file" id="up"><br><button class="ghost small" onclick="up()">Upload here</button><br><br>
<button class="ghost small" onclick="bmAdd()">+ bookmark this</button> <button class="ghost small" onclick="bmShow()">bookmarks</button>`,
[['Close',closeModal,'ghost']])}
async function mk(k){const n=$('nn').value.trim();if(!n)return toast('enter name');try{await api(k==='dir'?'/api/mkdir':'/api/mkfile',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({dir:cur().path,name:n})});closeModal();reload()}catch(e){toast(e.message)}}
async function up(){const f=$('up').files[0];if(!f)return toast('pick file');if(f.size>25*1024*1024)return toast('>25MB: use split or adb push');
const b=await f.arrayBuffer();let bin='';const u8=new Uint8Array(b);for(let i=0;i<u8.length;i+=8192){bin+=String.fromCharCode.apply(null,u8.subarray(i,i+8192))}
try{await api('/api/upload_b64',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({dir:cur().path,name:f.name,b64:btoa(bin)})});toast('uploaded');closeModal();reload()}catch(e){toast(e.message)}}
function bmAdd(){let b=[];try{b=JSON.parse(localStorage.fmx_bm||'[]')}catch(e){}b.push(cur().path);localStorage.fmx_bm=JSON.stringify([...new Set(b)]);toast('bookmarked')}
function bmShow(){let b=[];try{b=JSON.parse(localStorage.fmx_bm||'[]')}catch(e){}openModal('Bookmarks',b.map(x=>`<div><button class="ghost small" onclick="nav('${x}');closeModal()">${esc(x)}</button></div>`).join('')||'none',[['Close',closeModal]])}
function openHelp(){openModal('FMX help','<div class="kv"><div>Tap folder/file to open. Tick checkbox for multi-select.</div><div>Copy/Cut then Paste. Tabs on top, roots = Home/sdcard.</div><div>ZIP/APK tap to browse + extract. Text tap to edit.</div><div>Run: <b>python3 filemanager.py --port 8080</b></div><div>Termux: run <b>termux-setup-storage</b> once for /sdcard.</div></div>',[['Close',closeModal]])}
$('q').addEventListener('keydown',async e=>{if(e.key!=='Enter')return;const v=e.target.value.trim();if(!v)return reload();
try{const j=await api('/api/search?base='+encodeURIComponent(cur().path)+'&q='+encodeURIComponent(v));
openModal('Search: '+v,j.results.slice(0,200).map(r=>`<div><small><a href="#" onclick="nav('${esc(r.path.substring(0,r.path.lastIndexOf('/'))||'/')}');closeModal();return false">${esc(r.name)}</a> - ${esc(r.path)}</small></div>`).join('')||'no results',[['Close',closeModal]])}catch(err){toast(err.message)}});
init();
if('serviceWorker' in navigator){navigator.serviceWorker.register('/sw.js').catch(()=>{})}
</script>
</body></html>
"""


def run(host="127.0.0.1", port=8080, quiet=False):
    """Start server (also used embedded from Android/Chaquopy)."""
    mimetypes.init()
    srv = ThreadingHTTPServer((host, port), Handler)
    if not quiet:
        print(f"{APP_NAME} v{VERSION}")
        print(f"Serving HOME={HOME}")
        print(f"Open: http://{host}:{port}")
        print("Press Ctrl+C to stop.")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    return srv


def main():
    ap = argparse.ArgumentParser(description="FMX all-in-one file manager")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--host", default="127.0.0.1")
    args = ap.parse_args()
    run(args.host, args.port)


if __name__ == "__main__":
    main()
