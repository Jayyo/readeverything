"""Conversion server: auth, limits and real conversions (when LibreOffice is there)."""
import os
import shutil
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import convert_server  # noqa: E402

SAMPLES = Path(__file__).resolve().parents[2] / "samples"
TOKEN = "test-token"


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("CONVERT_MAX_MB", "1")
    workdir = Path(os.environ.get("CONVERT_TEST_WORKDIR", tmp_path / "work"))
    app = convert_server.create_app(token=TOKEN, workdir=workdir)
    return app.test_client()


def auth(**extra):
    return {"Authorization": f"Bearer {TOKEN}", **extra}


def upload(client, name, data, headers=None):
    import io
    return client.post("/convert", data={"file": (io.BytesIO(data), name)},
                       headers=headers if headers is not None else auth(),
                       content_type="multipart/form-data")


def test_refuses_to_start_without_a_token(monkeypatch):
    monkeypatch.delenv("CONVERT_TOKEN", raising=False)
    monkeypatch.delenv("CONVERT_ALLOW_NO_AUTH", raising=False)
    with pytest.raises(SystemExit):
        convert_server.create_app()


def test_token_is_required(client):
    assert client.get("/health").status_code == 401
    assert client.get("/health", headers={"Authorization": "Bearer nope"}).status_code == 401
    assert upload(client, "a.docx", b"x", headers={}).status_code == 401
    r = client.get("/health", headers=auth())
    ok = r.get_json()["ok"]
    assert r.status_code == (200 if ok else 503) and "hwpx" in r.get_json()["formats"]
    assert r.headers["Cache-Control"] == "no-store"


def test_rejects_unsupported_types(client):
    assert upload(client, "a.exe", b"MZ").status_code == 415
    assert upload(client, "noext", b"x").status_code == 415
    assert upload(client, "a.docx", b"").status_code == 400
    assert client.post("/convert", headers=auth()).status_code == 400


def test_upload_limit(client):
    assert upload(client, "a.docx", b"x" * (1024 * 1024 + 128 * 1024)).status_code == 413
    # exactly the advertised size still fits, framing included
    assert upload(client, "a.exe", b"x" * (1024 * 1024)).status_code == 415


def test_busy_when_the_queue_is_full(client):
    conv = client.application.config["converter"]
    conv._waiting = conv.queue + 1
    assert upload(client, "a.docx", b"PK..").status_code == 503


def test_broken_document_is_a_422(client):
    if not shutil.which("soffice"):
        pytest.skip("needs LibreOffice")
    broken = (SAMPLES / "sample.docx").read_bytes()[:3000]     # cut-off ZIP
    r = upload(client, "a.docx", broken)
    assert r.status_code == 422, r.status_code


@pytest.mark.parametrize("name", ["sample.docx", "sample.xlsx", "sample.pptx"])
def test_office_files_become_pdf(client, name):
    if not shutil.which("soffice"):
        pytest.skip("needs LibreOffice")
    r = upload(client, name, (SAMPLES / name).read_bytes())
    assert r.status_code == 200, r.get_json()
    assert r.data.startswith(b"%PDF-") and r.mimetype == "application/pdf"


def test_hwpx_becomes_pdf(client):
    """Needs the H2Orestart extension in the server's LibreOffice profile."""
    if os.environ.get("CONVERT_TEST_HWP") != "1":
        pytest.skip("set CONVERT_TEST_HWP=1 (and CONVERT_TEST_WORKDIR) with H2Orestart installed")
    r = upload(client, "sample.hwpx", (SAMPLES / "sample.hwpx").read_bytes())
    assert r.status_code == 200, r.get_json()
    assert r.data.startswith(b"%PDF-")


def test_timeout_kills_libreoffice(client):
    if not shutil.which("soffice"):
        pytest.skip("needs LibreOffice")
    conv = client.application.config["converter"]
    conv.timeout = 0.05
    r = upload(client, "sample.docx", (SAMPLES / "sample.docx").read_bytes())
    assert r.status_code == 504
    assert not (conv.profile / ".lock").exists()
    import subprocess
    left = subprocess.run(["pgrep", "-f", str(conv.profile)], capture_output=True, text=True).stdout
    assert left.strip() == ""


def test_health_is_503_without_libreoffice(tmp_path, monkeypatch):
    monkeypatch.setattr(convert_server.shutil, "which", lambda name: None)
    app = convert_server.create_app(token=TOKEN, workdir=tmp_path / "w")
    r = app.test_client().get("/health", headers=auth())
    assert r.status_code == 503 and r.get_json()["ok"] is False
