import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from package_onprem import build_archive


class PackageOnpremTest(unittest.TestCase):
    def test_archive_contains_deployment_source_but_no_local_secrets_or_logs(self):
        project_root = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as temporary_directory:
            archive_path = Path(temporary_directory) / "qms-onprem.zip"
            build_archive(project_root, archive_path)

            with zipfile.ZipFile(archive_path) as archive:
                self.assertIsNone(archive.testzip())
                names = set(archive.namelist())
                self.assertIn("qms-onprem/docker-compose.onprem.yml", names)
                self.assertIn("qms-onprem/backend/src/main/java/com/pcbqms/common/logging/RequestLoggingFilter.java", names)
                self.assertIn("qms-onprem/frontend/src/auth/oidc.ts", names)
                self.assertIn("qms-onprem/frontend/docker-entrypoint.d/40-runtime-config.sh", names)
                self.assertIn("qms-onprem/ocr-service/app/main.py", names)
                self.assertFalse(any(name.endswith(".env.secure.local") for name in names))
                self.assertFalse(any(name.endswith(".log") for name in names))
                self.assertFalse(any("node_modules" in name or "/target/" in name for name in names))
                manifest = json.loads(archive.read("qms-onprem/MANIFEST.sha256.json"))
                self.assertEqual(len(manifest), len(names) - 1)
                self.assertEqual(set(manifest), {name.removeprefix("qms-onprem/") for name in names if not name.endswith("MANIFEST.sha256.json")})
                for member, digest in manifest.items():
                    self.assertEqual(hashlib.sha256(archive.read(f"qms-onprem/{member}")).hexdigest(), digest)


if __name__ == "__main__":
    unittest.main()
