#!/usr/bin/env python3
"""
Readeverything conversion server.

Wraps LibreOffice (soffice --headless --convert-to pdf, on the
DEFAULT profile so the H2Orestart HWP/HWPX import filter is available) behind a tiny HTTP API.
Converts office (doc/docx/ppt/pptx/xls/xlsx), hwp, hwpx, rtf, odt, etc. -> PDF.

Endpoints:
  POST /convert   multipart field "file"  ->  application/pdf
  GET  /health                            ->  {ok, soffice, cached}

LibreOffice serialises on the DEFAULT profile, so conversions are guarded by a global lock.
"""
import hashlib
import os
import shutil
import subprocess
import tempfile
import threading
from pathlib import Path

from flask import Flask, abort, jsonify, request, send_file

app = Flask(__name__)

CACHE = Path(os.environ.get("RE_CACHE", "/tmp/re_convert_cache"))
CACHE.mkdir(parents=True, exist_ok=True)
SOFFICE = shutil.which("libreoffice") or shutil.which("soffice")
TIMEOUT = int(os.environ.get("RE_TIMEOUT", "150"))

# LibreOffice on the DEFAULT profile allows only one instance at a time.
_lock = threading.Lock()


def _convert_to_pdf(data: bytes, filename: str) -> Path:
    sha = hashlib.sha256(data).hexdigest()
    out_pdf = CACHE / f"{sha}.pdf"
    if out_pdf.exists() and out_pdf.stat().st_size > 0:
        return out_pdf

    with tempfile.TemporaryDirectory(prefix="re_conv_") as td:
        tdp = Path(td)
        # Keep the original extension so LibreOffice selects the correct import filter
        # (critical for .hwp/.hwpx routing to the H2Orestart filter).
        name = os.path.basename(filename) or "input"
        if "." not in name:
            name += ".bin"
        src = tdp / name
        src.write_bytes(data)

        with _lock:
            subprocess.run(
                [
                    SOFFICE,
                    "--headless",
                    "--norestore",
                    "--convert-to",
                    "pdf",
                    "--outdir",
                    str(tdp),
                    str(src),
                ],
                timeout=TIMEOUT,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )

        produced = tdp / (src.stem + ".pdf")
        if not produced.exists() or produced.stat().st_size == 0:
            abort(500, description=f"conversion produced no pdf for {name}")
        shutil.copy(produced, out_pdf)
    return out_pdf


@app.post("/convert")
def convert():
    f = request.files.get("file")
    if f is None:
        abort(400, description="missing multipart field 'file'")
    data = f.read()
    if not data:
        abort(400, description="empty file")
    out_pdf = _convert_to_pdf(data, f.filename or "input")
    return send_file(out_pdf, mimetype="application/pdf", as_attachment=False)


@app.get("/health")
def health():
    return jsonify(
        ok=SOFFICE is not None,
        soffice=SOFFICE,
        cached=len(list(CACHE.glob("*.pdf"))),
    )


if __name__ == "__main__":
    # threaded=False keeps requests serial; the lock already guards LibreOffice anyway.
    app.run(host="0.0.0.0", port=int(os.environ.get("RE_PORT", "3000")), threaded=True)
