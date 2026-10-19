"""Build the Linux Docker Compose deployment archive from an explicit source list."""

import argparse
import hashlib
import json
import zipfile
from datetime import datetime
from pathlib import Path


SOURCE_FILES = (
    ".env.onprem.example",
    "docker-compose.onprem.yml",
    "ONPREM_DEPLOYMENT.md",
    "backend/.dockerignore",
    "backend/Dockerfile",
    "backend/pom.xml",
    "frontend/.dockerignore",
    "frontend/Dockerfile",
    "frontend/index.html",
    "frontend/nginx.conf",
    "frontend/package.json",
    "frontend/package-lock.json",
    "frontend/vite.config.ts",
    "frontend/tsconfig.json",
    "frontend/tsconfig.app.json",
    "frontend/tsconfig.node.json",
    "ocr-service/.dockerignore",
    "ocr-service/Dockerfile",
    "ocr-service/requirements.txt",
)
SOURCE_DIRS = (
    "backend/src",
    "frontend/src",
    "frontend/public",
    "frontend/docker-entrypoint.d",
    "ocr-service/app",
)
EXCLUDED_PARTS = {"__pycache__", "node_modules", "target", ".git"}
EXCLUDED_SUFFIXES = {".log", ".pyc"}


def build_archive(project_root: Path, archive_path: Path) -> Path:
    project_root = project_root.resolve()
    files = [project_root / path for path in SOURCE_FILES]
    for source_dir in SOURCE_DIRS:
        directory = project_root / source_dir
        if not directory.is_dir():
            raise FileNotFoundError(directory)
        files.extend(path for path in directory.rglob("*") if path.is_file())

    archive_path.parent.mkdir(parents=True, exist_ok=True)
    manifest = {}
    with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(set(files)):
            relative = path.relative_to(project_root)
            if path.is_symlink() or any(part in EXCLUDED_PARTS for part in relative.parts):
                continue
            if path.suffix.lower() in EXCLUDED_SUFFIXES:
                continue
            if not path.is_file():
                raise FileNotFoundError(path)
            data = path.read_bytes()
            member = relative.as_posix()
            archive.writestr(f"qms-onprem/{member}", data)
            manifest[member] = hashlib.sha256(data).hexdigest()
        archive.writestr("qms-onprem/MANIFEST.sha256.json", json.dumps(manifest, indent=2) + "\n")
    return archive_path


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="ZIP output path")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output or root / "outputs" / f"qms-onprem-{datetime.now():%Y%m%d-%H%M%S}.zip"
    print(build_archive(root, output))
