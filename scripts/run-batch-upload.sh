#!/bin/bash
# Runs the UPLOAD-BATCH command using the batchItems defined in the given config file.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_lib.sh"

usage() {
    echo "Usage: $0 --config <config.json>"
    exit 1
}

CONFIG=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --config) CONFIG="$2"; shift 2 ;;
        *) usage ;;
    esac
done
[[ -z "$CONFIG" ]] && usage

build_if_needed
"$BIN" "$CONFIG" UPLOAD-BATCH
