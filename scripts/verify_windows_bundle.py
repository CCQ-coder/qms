"""Verify and smoke-test a packaged Windows QMS app image without a database."""

import argparse
import base64
import hashlib
import json
import os
import socket
import subprocess
import tempfile
import time
import zipfile
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import urlopen


def verify(archive_path: Path) -> None:
    with zipfile.ZipFile(archive_path) as archive:
        manifest = json.loads(archive.read("qms-windows/MANIFEST.sha256.json"))
        required = {"QMS.exe", "app/qms-api.jar", "runtime/bin/server/jvm.dll",
                    "start-qms-windows.ps1", "一键启动QMS.cmd", ".env.windows.example",
                    "WINDOWS_DEPLOYMENT.md", "RUN_WINDOWS.md"}
        missing = required - manifest.keys()
        if missing:
            raise AssertionError(f"Missing packaged files: {sorted(missing)}")
        if any(name.endswith(".env.windows") or name.startswith(("logs/", "data/", "ocr/"))
               or name == "start-ocr-windows.ps1" for name in manifest):
            raise AssertionError("Private data or OCR files entered the archive")
        for name, expected in manifest.items():
            actual = hashlib.sha256(archive.read(f"qms-windows/{name}")).hexdigest()
            if actual != expected:
                raise AssertionError(f"SHA-256 mismatch: {name}")

        with tempfile.TemporaryDirectory(prefix="qms-windows-smoke-") as temporary_directory:
            root = Path(temporary_directory)
            for member in archive.infolist():
                if member.filename.startswith(("qms-windows/runtime/", "qms-windows/app/")) or member.filename in {
                    "qms-windows/QMS.exe", "qms-windows/start-qms-windows.ps1",
                    "qms-windows/一键启动QMS.cmd"
                }:
                    archive.extract(member, root)
            image = root / "qms-windows"
            log_file = image / "logs" / "qms-api.log"
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                port = probe.getsockname()[1]
            (image / ".env.windows").write_text("\n".join([
                "QMS_DB_URL=jdbc:mysql://127.0.0.1:1/qms?useUnicode=true&characterEncoding=utf8",
                "QMS_DB_USERNAME=smoke",
                "QMS_DB_PASSWORD=smoke",
                "QMS_LOCAL_LOGIN_JWT_SECRET=" + base64.b64encode(b"smoke-test-only-32-byte-secret!!!!").decode(),
                "SERVER_PORT=" + str(port),
            ]), encoding="utf-8")
            environment = os.environ.copy()
            environment.update({
                "SPRING_FLYWAY_ENABLED": "false",
                "SPRING_MAIN_LAZY_INITIALIZATION": "true",
                "QMS_AUTO_INSPECTION_ENABLED": "false",
                "QMS_EQUIPMENT_TELEMETRY_RETENTION_ENABLED": "false",
            })
            with tempfile.TemporaryFile() as output:
                process = subprocess.Popen(["cmd.exe", "/d", "/c", str(image / "一键启动QMS.cmd")],
                                           cwd=image, env=environment, stdin=subprocess.DEVNULL,
                                           stdout=output, stderr=subprocess.STDOUT,
                                           creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
                try:
                    base_url = f"http://127.0.0.1:{port}"
                    for _ in range(30):
                        if process.poll() is not None:
                            break
                        try:
                            with urlopen(base_url + "/actuator/info", timeout=2) as response:
                                if response.status == 200:
                                    break
                        except (HTTPError, URLError):
                            time.sleep(1)
                    else:
                        raise AssertionError("Bundled QMS launcher did not start within 30 seconds")
                    if process.poll() is not None:
                        output.seek(0)
                        log_tail = log_file.read_text(encoding="utf-8", errors="replace")[-4000:] if log_file.is_file() else "<no file log>"
                        raise AssertionError(f"Windows one-click launcher exited ({process.returncode}):\n"
                                             + output.read().decode(errors="replace")[-4000:] + "\nLOG:\n" + log_tail)
                    with urlopen(base_url + "/", timeout=5) as response:
                        request_id = response.headers.get("X-Request-ID", "")
                        if response.status != 200 or b'<div id="app"></div>' not in response.read():
                            raise AssertionError("Bundled frontend did not load")
                    if len(request_id) != 32 or not log_file.is_file():
                        raise AssertionError("Request ID or file logging is missing")
                    log_text = log_file.read_text(encoding="utf-8")
                    if request_id not in log_text or "request GET / status=200" not in log_text:
                        raise AssertionError("Request ID was not written to the file log")
                finally:
                    if process.poll() is None:
                        subprocess.run(["taskkill.exe", "/PID", str(process.pid), "/T", "/F"],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=5)
                    # Windows can keep jvm.dll mapped briefly after the launcher exits.
                    time.sleep(1)
    print(f"Verified {len(manifest)} files, hashes, Windows one-click launcher and request file logging")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    verify(parser.parse_args().archive)
