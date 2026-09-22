package com.fmx.manager

import java.io.File
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tiny built-in HTTP share server (MiX/Material-Files-style "serve files
 * to other devices"): browse + download only, no dependencies.
 */
object ShareServer {
    private var thread: Thread? = null
    private var server: ServerSocket? = null
    private var _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    var url: String? = null
        private set

    fun start(root: File): String {
        stop()
        val srv = ServerSocket(0)
        server = srv
        val port = srv.localPort
        url = "http://${deviceIp()}:$port/"
        _running.value = true
        thread = Thread({
            try {
                while (!srv.isClosed) {
                    val s = srv.accept()
                    Thread({ handle(s, root) }).start()
                }
            } catch (_: Exception) {
            } finally {
                _running.value = false
            }
        }).apply { isDaemon = true; start() }
        return url!!
    }

    fun stop() {
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
        thread = null
        url = null
        _running.value = false
    }

    fun deviceIp(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()?.forEach { ni ->
                ni.inetAddresses?.asSequence()?.forEach { a ->
                    if (!a.isLoopbackAddress && a.hostAddress?.contains(":") == false) {
                        return a.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }

    private fun handle(s: Socket, root: File) {
        try {
            s.use {
                val ins = it.getInputStream().bufferedReader()
                val out = it.getOutputStream()
                val req = ins.readLine() ?: return
                // consume headers
                while (true) {
                    val h = ins.readLine() ?: break
                    if (h.isEmpty()) break
                }
                val parts = req.split(" ")
                if (parts.size < 2 || parts[0] != "GET") {
                    write(out, "HTTP/1.0 405 X\r\n\r\n")
                    return
                }
                val raw = parts[1]
                val base = root.canonicalPath
                if (raw.startsWith("/dl?p=")) {
                    val rel = URLDecoder.decode(raw.removePrefix("/dl?p="), "UTF-8")
                    val f = File(root, rel)
                    if (!f.canonicalPath.startsWith(base) || !f.isFile) {
                        write(out, "HTTP/1.0 404 X\r\n\r\nnot found")
                        return
                    }
                    val head = "HTTP/1.0 200 OK\r\nContent-Type: ${mimeOf(f)}\r\n" +
                        "Content-Length: ${f.length()}\r\n" +
                        "Content-Disposition: attachment; filename=\"${f.name}\"\r\n\r\n"
                    write(out, head)
                    f.inputStream().use { fi -> fi.copyTo(out) }
                } else {
                    val rel = URLDecoder.decode(raw.removePrefix("/").substringBefore("?"), "UTF-8")
                    val dir = if (rel.isBlank()) root else File(root, rel)
                    if (!dir.canonicalPath.startsWith(base) || !dir.isDirectory) {
                        write(out, "HTTP/1.0 404 X\r\n\r\nnot found")
                        return
                    }
                    val kids = dir.listFiles()?.sortedWith(
                        compareBy({ !it.isDirectory }, { it.name.lowercase() }),
                    ).orEmpty()
                    val sb = StringBuilder()
                    sb.append("<html><head><meta name=viewport content='width=device-width,initial-scale=1'>")
                    sb.append("<title>FMX share</title></head><body>")
                    sb.append("<h2>FMX · /").append(esc(rel)).append("</h2><ul>")
                    if (dir.canonicalPath != base) {
                        val up = File(rel).parent ?: ""
                        sb.append("<li><a href='/?p=").append(enc(up)).append("'>..</a></li>")
                    }
                    kids.forEach { k ->
                        val rp = (if (rel.isBlank()) "" else "$rel/") + k.name
                        if (k.isDirectory) {
                            sb.append("<li>[dir] <a href='/?p=").append(enc(rp)).append("'>")
                                .append(esc(k.name)).append("</a></li>")
                        } else {
                            sb.append("<li><a href='/dl?p=").append(enc(rp)).append("'>")
                                .append(esc(k.name)).append("</a> (")
                                .append(humanSize(k.length())).append(")</li>")
                        }
                    }
                    sb.append("</ul></body></html>")
                    val body = sb.toString().toByteArray()
                    write(out, "HTTP/1.0 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\n\r\n")
                    out.write(body)
                }
                out.flush()
            }
        } catch (_: Exception) {
        }
    }

    private fun write(out: java.io.OutputStream, s: String) = out.write(s.toByteArray())
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
