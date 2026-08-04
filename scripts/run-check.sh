#!/bin/bash
# Audits objects in S3 without downloading them: reports whether each one still has its
# encryption flag and stored checksum. Exits non-zero if any object has a problem.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_lib.sh"

usage() {
    echo "Usage: $0 --config <config.json> [key-prefix]"
    echo "  key-prefix: only check objects starting with this (default: all objects)"
    exit 1
}

CONFIG=""
ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --config) CONFIG="$2"; shift 2 ;;
        *) ARGS+=("$1"); shift ;;
    esac
done
[[ -z "$CONFIG" || ${#ARGS[@]} -gt 1 ]] && usage

build_if_needed
"$BIN" "$CONFIG" CHECK "${ARGS[0]:-}"
