import base64
import hashlib
import json
import os
import re
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

from package_windows import create_archive


class PackageWindowsTest(unittest.TestCase):
    def test_windows_example_and_upgrade_guide_cover_telemetry_gateway(self):
        windows = Path(__file__).resolve().parents[1] / "deploy" / "windows"
        example = (windows / ".env.windows.example").read_text(encoding="utf-8")
        guide = (windows / "WINDOWS_DEPLOYMENT.md").read_text(encoding="utf-8")
        self.assertIn("QMS_GATEWAY_SHARED_TOKEN=REPLACE_WITH_SHARED_TOKEN", example)
        self.assertIn("QMS_GATEWAY_TELEMETRY_TENANT_CODE=REPLACE_WITH_ACTIVE_TENANT_CODE", example)
        self.assertIn("QMS_GATEWAY_TELEMETRY_TENANT_CODE", guide)
        self.assertIn("Bearer Token", guide)

    @unittest.skipUnless(os.name == "nt", "Windows PowerShell 5.1 test")
    def test_documented_jwt_command_generates_32_byte_secret_in_windows_powershell(self):
        document = (Path(__file__).resolve().parents[1] / "deploy" / "windows" /
                    "WINDOWS_DEPLOYMENT.md").read_text(encoding="utf-8")
        command = next(line.strip() for line in re.findall(r"```powershell\n(.*?)```", document, re.DOTALL)
                       if "RandomNumberGenerator" in line)
        result = subprocess.run(["powershell.exe", "-NoProfile", "-Command", command],
                                check=True, capture_output=True, text=True, timeout=10)
        self.assertEqual(len(base64.b64decode(result.stdout.strip(), validate=True)), 32)

    def test_windows_powershell_scripts_have_utf8_bom(self):
        scripts = Path(__file__).resolve().parents[1] / "deploy" / "windows"
        for script in scripts.glob("*.ps1"):
            self.assertTrue(script.read_bytes().startswith(b"\xef\xbb\xbf"), script.name)

    def test_archive_contains_runtime_and_scripts_without_local_configuration(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            image = root / "QMS"
            (image / "app").mkdir(parents=True)
            (image / "runtime" / "bin").mkdir(parents=True)
            (image / "logs").mkdir()
            (image / "QMS.exe").write_bytes(b"launcher")
            (image / "app" / "qms-api.jar").write_bytes(b"jar")
            (image / "runtime" / "bin" / "server").mkdir()
            (image / "runtime" / "bin" / "server" / "jvm.dll").write_bytes(b"jvm")
            (image / "ocr").mkdir()
            (image / "ocr" / "requirements.txt").write_text("paddleocr")
            (image / "start-ocr-windows.ps1").write_text("ocr")
            (image / "start-qms-windows.ps1").write_text("start")
            (image / "一键启动QMS.cmd").write_text("start")
            (image / ".env.windows.example").write_text("example")
            (image / ".env.windows").write_text("real-secret")
            (image / "logs" / "qms-api.log").write_text("private-log")
            archive_path = root / "windows.zip"

            create_archive(image, archive_path)

            with zipfile.ZipFile(archive_path) as archive:
                names = set(archive.namelist())
                self.assertIn("qms-windows/QMS.exe", names)
                self.assertIn("qms-windows/app/qms-api.jar", names)
                self.assertIn("qms-windows/runtime/bin/server/jvm.dll", names)
                self.assertIn("qms-windows/.env.windows.example", names)
                self.assertIn("qms-windows/一键启动QMS.cmd", names)
                self.assertNotIn("qms-windows/.env.windows", names)
                self.assertNotIn("qms-windows/logs/qms-api.log", names)
                self.assertNotIn("qms-windows/ocr/requirements.txt", names)
                self.assertNotIn("qms-windows/start-ocr-windows.ps1", names)
                self.assertIsNone(archive.testzip())
                manifest = json.loads(archive.read("qms-windows/MANIFEST.sha256.json"))
                self.assertEqual(set(manifest), {name.removeprefix("qms-windows/") for name in names
                                                 if name != "qms-windows/MANIFEST.sha256.json"})
                for name, digest in manifest.items():
                    self.assertEqual(hashlib.sha256(archive.read(f"qms-windows/{name}")).hexdigest(), digest)


if __name__ == "__main__":
    unittest.main()
