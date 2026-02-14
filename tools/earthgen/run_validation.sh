#!/usr/bin/env bash
set -euo pipefail

PYTHON_BIN="${1:-.venv-earthgen/bin/python}"

if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "Python executable not found: $PYTHON_BIN" >&2
  exit 2
fi

"$PYTHON_BIN" -m pytest tools/earthgen/tests -q
