#!/usr/bin/env python3
"""Readeverything conversion server: office and HWP/HWPX documents in, PDF out.

    POST /convert   multipart field "file"  ->  application/pdf
    GET  /health                            ->  {"ok": true, ...}

Both need `Authorization: Bearer <CONVERT_TOKEN>`.

LibreOffice does the work (`soffice --headless --convert-to pdf`); HWP and
HWPX need the H2Orestart extension. One conversion runs at a time on a
profile owned by this process, a few more requests may wait, the rest get
503 (the limit applies to conversions; uploads are received first).
Input and output files are deleted after each request; the LibreOffice
profile stays.

Environment:
  CONVERT_TOKEN            bearer token clients must send (required)
  CONVERT_ALLOW_NO_AUTH=1  run without a token; for trying it on localhost only
  CONVERT_MAX_MB           upload limit in MB (default 50)
  CONVERT_TIMEOUT          seconds per conversion (default 120)
  CONVERT_QUEUE            requests allowed to wait for the converter (default 2)
  CONVERT_WORKDIR          LibreOffice profile and scratch space (default ./work)
  HOST, PORT               listen address (default 127.0.0.1:3000)
"""

from __future__ import annotations

import hmac
import io
import logging
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

from flask import Flask, abort, jsonify, request, send_file

ALLOWED = {"doc", "docx", "odt", "rtf", "ppt", "pptx", "odp", "xls", "xlsx", "ods",
           "hwp", "hwpx"}
MAX_PDF_BYTES = 300 * 1024 * 1024

log = logging.getLogger("convert")


def _int_env(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, default))
    except ValueError:
        return default


class Converter:
    """One LibreOffice at a time, on a private profile."""

    def __init__(self, workdir: Path, timeout: int, queue: int):
        self.workdir = workdir
        self.profile = workdir / "profile"
        self.timeout = timeout
        self.soffice = shutil.which("soffice") or shutil.which("libreoffice")
        self._lock = threading.Lock()
        self._waiting = 0
        self._waiting_lock = threading.Lock()
        self.queue = queue
        workdir.mkdir(parents=True, exist_ok=True)
        # Jobs left behind by a killed process.
        for stale in workdir.glob("job-*"):
            shutil.rmtree(stale, ignore_errors=True)

    @property
    def busy(self) -> bool:
        return self._lock.locked()

    def convert(self, data: bytes, ext: str) -> bytes:
        with self._waiting_lock:
            if self._waiting > self.queue:
                raise Busy()
            self._waiting += 1
        try:
            if not self._lock.acquire(timeout=self.timeout):
                raise Busy()
            try:
                return self._run(data, ext)
            finally:
                self._lock.release()
        finally:
            with self._waiting_lock:
                self._waiting -= 1

    def _run(self, data: bytes, ext: str) -> bytes:
        if not self.soffice:
            raise ConversionError("LibreOffice (soffice) is not installed")
        with tempfile.TemporaryDirectory(dir=self.workdir, prefix="job-") as td:
            src_dir, out_dir = Path(td, "in"), Path(td, "out")
            src_dir.mkdir()
            out_dir.mkdir()
            # The name is ours, only the extension comes from the client; it
            # selects LibreOffice's import filter (H2Orestart for hwp/hwpx).
            src = src_dir / f"document.{ext}"
            src.write_bytes(data)
            argv = [self.soffice, f"-env:UserInstallation={self.profile.as_uri()}",
                    "--headless", "--norestore", "--nologo", "--nodefault",
                    "--convert-to", "pdf", "--outdir", str(out_dir), str(src)]
            env = {k: v for k, v in os.environ.items() if not k.startswith("CONVERT_")}
            proc = subprocess.Popen(argv, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                    start_new_session=True, env=env)
            try:
                proc.wait(timeout=self.timeout)
            except subprocess.TimeoutExpired:
                # soffice forks soffice.bin; take the whole process group down
                # and drop the profile lock it leaves behind.
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait()
                (self.profile / ".lock").unlink(missing_ok=True)
                raise Timeout()
            pdf = out_dir / "document.pdf"
            if not pdf.is_file():
                raise ConversionError("LibreOffice could not convert this file")
            size = pdf.stat().st_size
            if size == 0 or size > MAX_PDF_BYTES:
                raise ConversionError("unexpected output size")
            out = pdf.read_bytes()
            if not out.startswith(b"%PDF-"):
                raise ConversionError("output is not a PDF")
            return out


class Busy(Exception):
    pass


class Timeout(Exception):
    pass


class ConversionError(Exception):
    pass


def create_app(token: str | None = None, allow_no_auth: bool | None = None,
               workdir: Path | None = None) -> Flask:
    token = os.environ.get("CONVERT_TOKEN", "") if token is None else token
    if allow_no_auth is None:
        allow_no_auth = os.environ.get("CONVERT_ALLOW_NO_AUTH") == "1"
    if not token and not allow_no_auth:
        raise SystemExit("Set CONVERT_TOKEN (or CONVERT_ALLOW_NO_AUTH=1 for local testing).")

    max_mb = _int_env("CONVERT_MAX_MB", 50)
    converter = Converter(workdir or Path(os.environ.get("CONVERT_WORKDIR", "work")).resolve(),
                          timeout=_int_env("CONVERT_TIMEOUT", 120),
                          queue=_int_env("CONVERT_QUEUE", 2))
    app = Flask(__name__)
    # The limit is on the whole request; leave room for the multipart framing.
    app.config["MAX_CONTENT_LENGTH"] = max_mb * 1024 * 1024 + 64 * 1024
    app.config["converter"] = converter

    def error(status: int, message: str):
        return jsonify(error=message), status

    @app.before_request
    def check_token():
        if not token:
            return None
        sent = request.headers.get("Authorization", "")
        if not hmac.compare_digest(sent.encode(), f"Bearer {token}".encode()):
            return error(401, "missing or wrong token")
        return None

    @app.errorhandler(413)
    def too_large(_e):
        return error(413, f"file is larger than {max_mb} MB")

    @app.after_request
    def no_store(resp):
        resp.headers["Cache-Control"] = "no-store"
        return resp

    @app.get("/health")
    def health():
        ok = converter.soffice is not None
        return jsonify(ok=ok, busy=converter.busy, max_mb=max_mb,
                       formats=sorted(ALLOWED)), (200 if ok else 503)

    @app.post("/convert")
    def convert():
        f = request.files.get("file")
        if f is None or not f.filename:
            return error(400, "send the document as multipart field 'file'")
        ext = f.filename.rsplit(".", 1)[-1].lower() if "." in f.filename else ""
        if ext not in ALLOWED:
            return error(415, f"unsupported type: .{ext}" if ext else "file name has no extension")
        data = f.read()
        if not data:
            return error(400, "empty file")
        started = time.monotonic()
        try:
            pdf = converter.convert(data, ext)
        except Busy:
            return error(503, "busy, try again shortly")
        except Timeout:
            log.warning("convert %s: timed out", ext)
            return error(504, "conversion timed out")
        except ConversionError as e:
            log.warning("convert %s: %s", ext, e)
            return error(422, str(e))
        log.info("convert %s: %d -> %d bytes in %.1fs", ext, len(data), len(pdf),
                 time.monotonic() - started)
        return send_file(io.BytesIO(pdf), mimetype="application/pdf",
                         download_name="document.pdf")

    return app


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    app = create_app()
    host = os.environ.get("HOST", "127.0.0.1")
    port = _int_env("PORT", 3000)
    try:
        from waitress import serve
    except ImportError:
        log.warning("waitress is not installed; using Flask's development server")
        app.run(host=host, port=port, threaded=True)
        return
    serve(app, host=host, port=port, threads=4,
          max_request_body_size=app.config["MAX_CONTENT_LENGTH"])


if __name__ == "__main__":
    sys.exit(main())
