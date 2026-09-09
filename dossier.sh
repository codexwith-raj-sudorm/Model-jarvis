#!/usr/bin/env bash
# Regenerate the one-file JARVIS dossier (narrative + changelog + ALL source).
# Run this after every debug/feature session (after appending to docs/CHANGELOG.md).
set -euo pipefail
cd "$(dirname "$0")"
python3 scripts/build_dossier.py "$@"
