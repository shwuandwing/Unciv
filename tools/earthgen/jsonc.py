from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any


_BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", flags=re.S)
_LINE_COMMENT_RE = re.compile(r"//.*?$", flags=re.M)
_TRAILING_COMMA_RE = re.compile(r",\s*([}\]])")


def parse_jsonc_text(text: str) -> Any:
    """Parse JSON-with-comments into Python objects.

    This is intentionally lightweight and sufficient for Unciv ruleset files:
    - Removes /* ... */ and // ... comments
    - Removes trailing commas before '}' or ']'
    """
    cleaned = _BLOCK_COMMENT_RE.sub("", text)
    cleaned = _LINE_COMMENT_RE.sub("", cleaned)
    cleaned = _TRAILING_COMMA_RE.sub(r"\1", cleaned)
    return json.loads(cleaned)


def parse_jsonc_file(path: Path) -> Any:
    return parse_jsonc_text(path.read_text(encoding="utf-8"))
