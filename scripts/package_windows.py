"""Build a portable Windows QMS bundle with its own Java runtime."""

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import zipfile
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EXCLUDED_DIRECTORIES = {"logs", "data", "ocr", "__pycache__"}


def create_archive(image: Path, archive_path: Path) -> Path:
    archive_path.parent.mkdir(parents=True, exist_ok=True)
    manifest = {}
    with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(image.rglob("*")):
            if not path.is_file() or path.is_symlink():
                continue
            relative = path.relative_to(image)
            if any(part in EXCLUDED_DIRECTORIES for part in relative.parts):
                continue
            if relative.name in {".env.windows", "start-ocr-windows.ps1"} or relative.suffix.lower() in {".log", ".pyc"}:
                continue
            member = relative.as_posix()
            archive.write(path, f"qms-windows/{member}")
            manifest[member] = hashlib.sha256(path.read_bytes()).hexdigest()
        archive.writestr("qms-windows/MANIFEST.sha256.json", json.dumps(manifest, indent=2) + "\n")
    return archive_path


def build_package(archive_path: Path, skip_build: bool = False) -> Path:
    if os.name != "nt":
        raise RuntimeError("Windows app-image packaging must run on Windows")
    if not skip_build:
        npm = shutil.which("npm.cmd")
        maven = shutil.which("mvn.cmd")
        if not npm or not maven:
            raise FileNotFoundError("npm.cmd and mvn.cmd are required to build the package")
        frontend_environment = os.environ.copy()
        frontend_environment["VITE_ROUTER_HASH"] = "true"
        subprocess.run([npm, "run", "build"], cwd=ROOT / "frontend", env=frontend_environment, check=True)
        maven_command = [maven, "-B", "-Pwindows-package", "-DskipTests"]
        local_repository = ROOT / ".m2" / "repository"
        if local_repository.is_dir():
            maven_command.append(f"-Dmaven.repo.local={local_repository.as_posix()}")
        subprocess.run([*maven_command, "clean", "package"], cwd=ROOT / "backend", check=True)

    jar = ROOT / "backend" / "target" / "qms-api-0.1.0-SNAPSHOT.jar"
    if not jar.is_file():
        raise FileNotFoundError(jar)
    jpackage = shutil.which("jpackage.exe")
    if not jpackage:
        raise FileNotFoundError("jpackage.exe from JDK 21 is required")

    with tempfile.TemporaryDirectory(prefix="qms-windows-package-") as temporary_directory:
        temporary_root = Path(temporary_directory)
        input_directory = temporary_root / "input"
        input_directory.mkdir()
        shutil.copy2(jar, input_directory / "qms-api.jar")
        app_images = temporary_root / "images"
        subprocess.run([
            jpackage, "--type", "app-image", "--name", "QMS", "--app-version", "0.1.0",
            "--input", str(input_directory), "--main-jar", "qms-api.jar",
            "--dest", str(app_images), "--win-console",
        ], check=True)
        image = app_images / "QMS"
        for source in (ROOT / "deploy" / "windows").iterdir():
            if source.is_file():
                shutil.copy2(source, image / source.name)
        return create_archive(image, archive_path)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--skip-build", action="store_true", help="Use the already built frontend and JAR")
    arguments = parser.parse_args()
    output = arguments.output or ROOT / "outputs" / f"qms-windows-{datetime.now():%Y%m%d-%H%M%S}.zip"
    print(build_package(output, arguments.skip_build))
