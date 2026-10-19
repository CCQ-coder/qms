"""Smoke test the Windows JAR's built-in web frontend without a database."""

import base64
import os
import socket
import subprocess
import tempfile
import time
import unittest
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import urlopen


ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "backend" / "target" / "qms-api-0.1.0-SNAPSHOT.jar"


class WindowsWebTest(unittest.TestCase):
    def test_jar_serves_frontend_from_same_origin(self):
        if not JAR.is_file():
            self.skipTest("Build the backend JAR before running this integration test")
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]

        environment = os.environ.copy()
        environment.update({
            "SERVER_PORT": str(port),
            "SPRING_FLYWAY_ENABLED": "false",
            "SPRING_MAIN_LAZY_INITIALIZATION": "true",
            "QMS_DB_URL": "jdbc:mysql://127.0.0.1:1/qms",
            "QMS_DB_USERNAME": "smoke",
            "QMS_DB_PASSWORD": "smoke",
            "QMS_BOOTSTRAP_TENANT_CODE": "",
            "QMS_BOOTSTRAP_ADMIN_USERNAME": "",
            "QMS_BOOTSTRAP_ADMIN_PASSWORD": "",
            "QMS_SECURITY_DEVELOPMENT_MODE": "false",
            "QMS_LOCAL_PASSWORD_LOGIN_ENABLED": "true",
            "QMS_LOCAL_LOGIN_JWT_SECRET": base64.b64encode(b"smoke-test-only-32-byte-secret!!!!").decode(),
            "QMS_AUTO_INSPECTION_ENABLED": "false",
            "QMS_EQUIPMENT_TELEMETRY_RETENTION_ENABLED": "false",
            "QMS_SERVE_FRONTEND": "true",
        })
        with tempfile.TemporaryFile() as output:
            process = subprocess.Popen(
                ["java", "-jar", str(JAR)], cwd=ROOT / "backend", env=environment,
                stdout=output, stderr=subprocess.STDOUT,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
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
                    self.fail("API did not become ready within 30 seconds")

                if process.poll() is not None:
                    output.seek(0)
                    self.fail("API exited before web check:\n" + output.read().decode(errors="replace")[-4000:])
                with urlopen(base_url + "/", timeout=5) as response:
                    self.assertEqual(response.status, 200)
                    self.assertIn('<div id="app"></div>', response.read().decode())
                with urlopen(base_url + "/runtime-config.js", timeout=5) as response:
                    self.assertEqual(response.status, 200)
            finally:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)


if __name__ == "__main__":
    unittest.main()
