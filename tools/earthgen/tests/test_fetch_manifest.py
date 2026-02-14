from __future__ import annotations

import json
import os
import shutil
import socket
import subprocess
import tempfile
import threading
import unittest
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
FETCH_SCRIPT = REPO_ROOT / "tools" / "earthgen" / "fetch_datasets.py"


class DatasetServer:
    def __init__(self, root: Path):
        self.root = root
        self.httpd: ThreadingHTTPServer | None = None
        self.thread: threading.Thread | None = None

    def start(self) -> None:
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            host, port = sock.getsockname()
        handler = lambda *args, **kwargs: SimpleHTTPRequestHandler(*args, directory=str(self.root), **kwargs)
        self.httpd = ThreadingHTTPServer(("127.0.0.1", port), handler)
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()

    @property
    def base_url(self) -> str:
        assert self.httpd is not None
        return f"http://127.0.0.1:{self.httpd.server_port}"

    def stop(self) -> None:
        if self.httpd is not None:
            self.httpd.shutdown()
            self.httpd.server_close()
        if self.thread is not None:
            self.thread.join(timeout=2)


class FetchManifestTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = Path(tempfile.mkdtemp(prefix="earthgen_fetch_test_"))
        self.server_root = self.temp_dir / "server"
        self.server_root.mkdir(parents=True)
        self.payload = self.server_root / "sample.bin"
        self.payload.write_bytes(b"earthgen-test-payload-v1\n")

        self.server = DatasetServer(self.server_root)
        self.server.start()

        self.cache_dir = self.temp_dir / "cache"
        self.catalog = self.temp_dir / "catalog.json"
        catalog_data = {
            "sample": {
                "url": f"{self.server.base_url}/sample.bin",
                "filename": "sample.bin",
                "description": "sample",
            }
        }
        self.catalog.write_text(json.dumps(catalog_data), encoding="utf-8")

    def tearDown(self) -> None:
        self.server.stop()
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def run_fetch(self) -> str:
        cmd = [
            "python3",
            str(FETCH_SCRIPT),
            "--cache-dir",
            str(self.cache_dir),
            "--dataset-catalog",
            str(self.catalog),
        ]
        result = subprocess.run(cmd, cwd=str(REPO_ROOT), capture_output=True, text=True, check=True)
        return result.stdout + result.stderr

    def test_manifest_schema_and_idempotent_second_run(self) -> None:
        out1 = self.run_fetch()
        self.assertIn("Wrote manifest", out1)

        manifest_path = self.cache_dir / "manifest.json"
        self.assertTrue(manifest_path.exists())
        manifest_text_1 = manifest_path.read_text(encoding="utf-8")
        manifest = json.loads(manifest_text_1)

        self.assertEqual(manifest["version"], 1)
        self.assertIn("datasets", manifest)
        self.assertIn("sample", manifest["datasets"])

        entry = manifest["datasets"]["sample"]
        for key in ["id", "url", "filename", "sha256", "size_bytes", "downloaded_at"]:
            self.assertIn(key, entry)

        out2 = self.run_fetch()
        self.assertIn("CACHE HIT", out2)
        self.assertIn("Manifest unchanged", out2)
        manifest_text_2 = manifest_path.read_text(encoding="utf-8")
        self.assertEqual(manifest_text_1, manifest_text_2)

    def test_corrupt_cache_file_triggers_redownload(self) -> None:
        self.run_fetch()
        cached_file = self.cache_dir / "sample.bin"
        original = cached_file.read_bytes()

        cached_file.write_bytes(b"corrupted\n")
        out = self.run_fetch()

        self.assertIn("checksum mismatch", out)
        self.assertEqual(cached_file.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
