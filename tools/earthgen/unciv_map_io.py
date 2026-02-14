from __future__ import annotations

import base64
import gzip
import json
from pathlib import Path
from typing import Any, Dict


def encode_map_payload(payload: Dict[str, Any]) -> str:
    raw_json = json.dumps(payload, separators=(",", ":"), ensure_ascii=False)
    compressed = gzip.compress(raw_json.encode("utf-8"), compresslevel=9)
    return base64.b64encode(compressed).decode("ascii")


def decode_map_payload(encoded: str) -> Dict[str, Any]:
    compressed = base64.b64decode(encoded.encode("ascii"))
    raw = gzip.decompress(compressed).decode("utf-8")
    return json.loads(raw)


def write_map_file(path: Path, payload: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = encode_map_payload(payload)
    path.write_text(encoded, encoding="utf-8")


def read_map_file(path: Path) -> Dict[str, Any]:
    return decode_map_payload(path.read_text(encoding="utf-8"))
